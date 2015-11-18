package com.sora.util.akatsuki;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
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
import com.sora.util.akatsuki.AkatsukiConfig.Flags;
import com.sora.util.akatsuki.AkatsukiConfig.OptFlags;
import com.sora.util.akatsuki.Utils.Defaults;
import com.sora.util.akatsuki.Utils.Values;
import com.sora.util.akatsuki.models.SourceTreeModel;
import com.sora.util.akatsuki.models.SourceTreeModel.Arrangement;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedOptions({ "akatsuki.loggingLevel", "akatsuki.allowTransient", "akatsuki.allowVolatile",
		"akatsuki.optFlags", "akatsuki.flags" })
public class AkatsukiProcessor extends AbstractProcessor {

	private ProcessorContext context;

	private Map<String, String> options;

	private static final Set<Class<? extends Annotation>> FIELD_ANNOTATIONS = ImmutableSet
			.of(With.class, Retained.class, Arg.class);

	private static final Set<Class<? extends Annotation>> SUPPORTED_ANNOTATIONS = ImmutableSet.of(
			TransformationTemplate.class, IncludeClasses.class, DeclaredConverter.class,
			TypeFilter.class, TypeConstraint.class, RetainConfig.class, ArgConfig.class,
			AkatsukiConfig.class);

	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		processingEnv.getMessager().printMessage(Kind.NOTE, "Processor started");
		this.context = new ProcessorContext(processingEnv);
		Log.verbose(context, "Processor context created...");
		Map<String, String> options = processingEnv.getOptions();
		if (options != null && !options.isEmpty()) {
			this.options = options;
			Log.verbose(context, "Options received: " + options);
		}

	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		// NOTE: process gets called multiple times if any other annotation
		// processor exists

		context.roundStarted();
		Log.verbose(context, "Begin processing round, roots: " + roundEnv.getRootElements());

		AkatsukiConfig config;

		final List<AkatsukiConfig> configs = findAnnotations(
				roundEnv.getElementsAnnotatedWith(AkatsukiConfig.class), AkatsukiConfig.class);
		if (configs.size() > 1) {
			context.messager().printMessage(Kind.ERROR,
					"Multiple @RetainConfig found, you can only have one config. Found:"
							+ configs.toString());
			context.roundFinished();
			return false;
		}
		config = configs.isEmpty() ? Defaults.of(AkatsukiConfig.class) : configs.get(0);
		if (options != null) {
			config = Values.of(AkatsukiConfig.class, config, options,
					methodName -> "akatsuki." + methodName);
		}

		Configuration configuration = new Configuration(config);
		Log.verbose(context, "Verifying configuration...");
		configuration.validate(context);
		// config is ready, we can log properly now
		Log.verbose(context, "Configuration loaded: " + configuration);
		context.setConfigForRound(configuration);

		// short circuit when compiler disabled
		if (context.config().flags().contains(Flags.DISABLE_COMPILER)) {
			Log.verbose(context, "DISABLE_COMPILER flag found, compiler disabled.");
			context.roundFinished();
			return false;
		}

		Log.verbose(context, "Building source tree...");
		SourceTreeModel model = SourceTreeModel.fromRound(context, roundEnv, FIELD_ANNOTATIONS);

		if (model == null) {
			context.messager().printMessage(Kind.ERROR, "Source tree verification failed");
			context.roundFinished();
			return true;
		}
		if (model.classModels(Arrangement.HEAD).isEmpty()) {
			Log.verbose(context,
					"Round has no elements, classes possibly originated from another annotation processor");
			context.roundFinished();
			return false;
		}
		Log.verbose(context, "Source tree built");
		Log.verbose(context, "Tree = " + model.toString());

		Log.verbose(context, "Resolving @TransformationTemplate...");
		List<TransformationTemplate> templates = findTransformationTemplates(roundEnv);
		Log.verbose(context, "Found " + templates.size());
		Log.verbose(context, "Resolving @DeclaredConverter...");
		List<DeclaredConverterModel> declaredConverters = findDeclaredConverters(roundEnv);
		Log.verbose(context, "Found " + declaredConverters.size());

		final TypeAnalyzerResolver resolver = new TypeAnalyzerResolver(templates,
				declaredConverters, model, context);
		Log.verbose(context, "TypeAnalyzerResolver created...");

		context.setBundleTypeResolverForRound(resolver);

		// bundle retainer first
		Log.verbose(context, "Generating classes for @Retained...");

		List<RetainedStateModel> retainedStateModels = model.classModels(Arrangement.HEAD).stream()
				.filter(m -> m.containsAnyAnnotation(Retained.class))
				.map(cm -> new RetainedStateModel(context, cm, model)).collect(Collectors.toList());
		Log.verbose(context, "Generated " + retainedStateModels.size() + ":");
		retainedStateModels.forEach(m -> Log.verbose(context, "\t" + m.classInfo().className));

		for (RetainedStateModel stateModel : retainedStateModels) {
			try {
				stateModel.writeToFile(processingEnv.getFiler());
			} catch (IOException e) {
				context.messager().printMessage(Kind.ERROR,
						"An error occurred while writing file:" + stateModel.classInfo());
				throw new RuntimeException(e);
			}
		}

		if (context.config().optFlags().contains(OptFlags.CLASS_LUT)
				&& !retainedStateModels.isEmpty()) {
			Log.verbose(context, "Generating additional classes for OptFlags.CLASS_LUT...");
			try {
				new RetainerLUTModel(context, retainedStateModels, roundEnv.getRootElements())
						.writeToFile(processingEnv.getFiler());
			} catch (IOException e) {
				context.messager().printMessage(Kind.ERROR,
						"An error occurred while writing cache class, "
								+ "try disabling OptFlags.VECTORIZE_INHERITANCE");
				throw new RuntimeException(e);
			}
		}

		Log.verbose(context, "Generating classes for @Arg...");
		List<ArgumentBuilderModel> argumentBuilderModels = model.classModels(Arrangement.FLATTENED)
				.stream()
//				.filter(m -> m.containsAnyAnnotation(Arg.class) || m.directSuperModel()
//						.map(mm -> mm.containsAnyAnnotation(Arg.class)).orElse(false))
				.map(cm -> new ArgumentBuilderModel(context, cm, model,
						Optional.of(Internal.BUILDER_CLASS_NAME)))
				.collect(Collectors.toList());
		Log.verbose(context, "Generated " + argumentBuilderModels.size() + ":");
		argumentBuilderModels.forEach(m -> Log.verbose(context, "\t" + m.classInfo().className));

		try {
			ArgumentBuildersModel argumentBuildersModel = new ArgumentBuildersModel(context,
					argumentBuilderModels);
			argumentBuildersModel.writeToFile(processingEnv.getFiler());

		} catch (IOException e) {
			context.messager().printMessage(Kind.ERROR,
					"An error occurred while writing argument builder");
			throw new RuntimeException(e);
		}
		context.roundFinished();
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
		classes.addAll(SUPPORTED_ANNOTATIONS);
		return classes.stream().map(Class::getName).collect(Collectors.toSet());
	}
}
