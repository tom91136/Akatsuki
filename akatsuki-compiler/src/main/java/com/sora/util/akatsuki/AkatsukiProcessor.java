package com.sora.util.akatsuki;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.tools.Diagnostic.Kind;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.sora.util.akatsuki.BundleRetainerModel.Action;
import com.sora.util.akatsuki.RetainConfig.Optimisation;
import com.sora.util.akatsuki.Utils.Defaults;
import com.sora.util.akatsuki.Utils.Values;
import com.sora.util.akatsuki.models.SourceTreeModel;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedOptions({ "akatsuki.loggingLevel", "akatsuki.optimisation", "akatsuki.restorePolicy" })
public class AkatsukiProcessor extends AbstractProcessor {

	private static RetainConfig config;

	private ProcessorContext context;

	private Map<String, String> options;

	private static final Set<Class<? extends Annotation>> FIELD_ANNOTATIONS = ImmutableSet
			.of(With.class, Retained.class, Arg.class);

	private static final Set<Class<? extends Annotation>> SUPPORT_ANNOTATIONS = ImmutableSet.of(
			TransformationTemplate.class, IncludeClasses.class, DeclaredConverter.class,
			TypeConstraint.class, RetainConfig.class);

	public static RetainConfig retainConfig() {
		return config;
	}

	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		this.context = new ProcessorContext(processingEnv);

		Map<String, String> options = processingEnv.getOptions();
		if (options != null && !options.isEmpty()) {
			this.options = options;
			context.messager().printMessage(Kind.OTHER, "option received:" + options);
		}
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		// NOTE: process gets called multiple times if any other annotation
		// processor exists

		// compiler options override @RetainConfig
		if (options == null) {
			final List<RetainConfig> configs = findAnnotations(
					roundEnv.getElementsAnnotatedWith(RetainConfig.class), RetainConfig.class);
			if (configs.size() > 1) {
				context.messager().printMessage(Kind.ERROR,
						"Multiple @RetainConfig found, you can only have one config. Found:"
								+ configs.toString());
			}
			config = configs.isEmpty() ? Defaults.of(RetainConfig.class) : configs.get(0);
		} else {
			config = Values.of(RetainConfig.class, Defaults.of(RetainConfig.class), options,
					methodName -> "akatsuki." + methodName);
		}

		// config is ready, we can log now
		Log.verbose(context, retainConfig().toString());

		SourceTreeModel model = SourceTreeModel.fromRound(context, roundEnv, FIELD_ANNOTATIONS);

		if (model == null) {
			context.messager().printMessage(Kind.ERROR, "verification failed");
			return true;
		}

		if (model.classModels().isEmpty()) {
			Log.verbose(context,
					"Round has no elements, classes possibly originated from another annotation processor");
			return false;
		}

		// bundle retainer first

		List<TransformationTemplate> templates = findTransformationTemplates(roundEnv);
		List<DeclaredConverterModel> declaredConverters = findDeclaredConverters(roundEnv);
		final TypeAnalyzerResolver resolver = new TypeAnalyzerResolver(templates,
				declaredConverters, model, context);

		List<BundleRetainerModel> retainerModels = model.forEachClassSerial((c, t) -> {
			BundleRetainerModel retainerModel = new BundleRetainerModel(context, c, t, resolver,
					Optional.of(fm -> fm.annotation(Retained.class)
							.map((retained) -> !retained.skip()).orElse(false)),
					EnumSet.allOf(Action.class), Optional.of((ctx, action, element, analysis) -> {

				Retained retained = element.model().annotation(Retained.class)
						.orElseThrow(AssertionError::new);
				// policy only works on objects as primitives have default
				// values which we can't really check for :(
				if (!ctx.utils().isPrimitive(element.fieldMirror())) {
					switch (retained.restorePolicy()) {
					case IF_NULL:
						analysis.wrap(s -> "if({{fieldName}} == null){\n" + s + "}\n");
						break;
					case IF_NOT_NULL:
						analysis.wrap(s -> "if({{fieldName}} != null){\n" + s + "}\n");
						break;
					default:
					case DEFAULT:
					case OVERWRITE:
						// do nothing
						break;
					}
				}
			}));
			retainerModel.writeSourceToFile(processingEnv.getFiler());
			return retainerModel;
		} , (e, m) -> {
			context.messager().printMessage(Kind.ERROR,
					"An error occurred while writing file: " + m.asClassInfo());
			throw new RuntimeException(e);
		});

		if (retainConfig().optimisation() != Optimisation.NONE && retainerModels != null) {

			try {
				new RetainerMappingModel(context, retainerModels, roundEnv.getRootElements(),
						retainConfig().optimisation()).writeSourceToFile(processingEnv.getFiler());
			} catch (IOException e) {
				context.messager().printMessage(Kind.ERROR,
						"An error occurred while writing cache class, "
								+ "try adding @RetainConfig(optimisation = Optimisation.NONE) to any class.");
				throw new RuntimeException(e);
			}
		}

		try {
			new ArgumentBuilderModel(context, model, resolver)
					.writeSourceToFile(processingEnv.getFiler());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return true;
	}

	private <T extends Annotation> List<T> findAnnotations(Set<? extends Element> elements,
			Class<T> clazz) {
		return elements.stream().map(e -> e.getAnnotation(clazz)).collect(Collectors.toList());
	}

	private List<TransformationTemplate> findTransformationTemplates(RoundEnvironment roundEnv) {
		final List<TransformationTemplate> templates = new ArrayList<>();
		// find all included
		final List<IncludeClasses> includeClasses = findAnnotations(
				roundEnv.getElementsAnnotatedWith(IncludeClasses.class), IncludeClasses.class);

		for (IncludeClasses classes : includeClasses) {
			context.utils().getClassArrayFromAnnotationMethod(classes::value)
					.forEach(t -> templates.addAll(Arrays.asList(
							t.asElement().getAnnotationsByType(TransformationTemplate.class))));
		}

		// find all directly annotated
		templates.addAll(
				findAnnotations(roundEnv.getElementsAnnotatedWith(TransformationTemplate.class),
						TransformationTemplate.class));

		return templates;
	}

	private List<DeclaredConverterModel> findDeclaredConverters(RoundEnvironment roundEnv) {
		final Set<? extends Element> elements = roundEnv
				.getElementsAnnotatedWith(DeclaredConverter.class);
		for (Element element : elements) {
			if (!context.utils().isAssignable(element.asType(),
					context.utils().of(TypeConverter.class), true)) {
				context.messager().printMessage(Kind.ERROR,
						"@DeclaredConverter can only be used on types that implement TypeConverter",
						element);
			}
		}
		return elements.stream()
				.map(e -> new DeclaredConverterModel((DeclaredType) e.asType(),
						e.getAnnotation(DeclaredConverter.class).value()))
				.collect(Collectors.toList());
	}

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		HashSet<Class<? extends Annotation>> classes = new HashSet<>();
		classes.addAll(FIELD_ANNOTATIONS);
		classes.addAll(SUPPORT_ANNOTATIONS);
		return classes.stream().map(Class::getName).collect(Collectors.toSet());
	}
}
