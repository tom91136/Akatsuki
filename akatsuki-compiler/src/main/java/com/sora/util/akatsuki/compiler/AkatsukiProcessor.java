package com.sora.util.akatsuki.compiler;

import com.google.auto.service.AutoService;
import com.sora.util.akatsuki.DeclaredConverter;
import com.sora.util.akatsuki.IncludeClasses;
import com.sora.util.akatsuki.RetainConfig;
import com.sora.util.akatsuki.RetainConfig.Optimisation;
import com.sora.util.akatsuki.TransformationTemplate;
import com.sora.util.akatsuki.TypeConverter;
import com.sora.util.akatsuki.compiler.BundleRetainerModel.FqcnModelMap;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes({ "com.sora.util.akatsuki.Retained",
		"com.sora.util.akatsuki.TransformationTemplate", "com.sora.util.akatsuki.IncludeClasses",
		"com.sora.util.akatsuki.DeclaredConverter", "com.sora.util.akatsuki.TypeConstraint",
		"com.sora.util.akatsuki.RetainConfig" })
public class AkatsukiProcessor extends AbstractProcessor implements ProcessorContext {

	private static final boolean DEBUG = false;

	private ProcessorUtils utils;

	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		this.utils = new ProcessorUtils(processingEnv.getTypeUtils(),
				processingEnv.getElementUtils());
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		// NOTE: process gets called multiple times if any other annotation
		// processor exists
		final List<TransformationTemplate> templates = findTransformationTemplates(roundEnv);
		final List<DeclaredConverterModel> declaredConverters = findDeclaredConverters(roundEnv);
		final FqcnModelMap map = BundleRetainerModel.findRetainedFields(this, roundEnv);

		final List<RetainConfig> configs = findAnnotations(
				roundEnv.getElementsAnnotatedWith(RetainConfig.class), RetainConfig.class);
		if (configs.size() > 1) {
			messager().printMessage(Kind.ERROR,
					"Multiple @RetainConfig found, you can only have one config. Found:"
							+ configs.toString());
		}

		if (map == null) {
			messager().printMessage(Kind.ERROR, "verification failed");
			return true;
		}

		if (map.isEmpty()) {
			messager().printMessage(Kind.NOTE,
					"round has no elements, classes possibly originated from another annotation processor. "
							+ "Root:" + annotations);
			return false;
		}

		map.values().stream().forEach(m -> {
			// messager().printMessage(Kind.OTHER, m.toString());
			try {
				m.writeSourceToFile(processingEnv.getFiler(), templates, map, declaredConverters);
			} catch (IOException e) {
				messager().printMessage(Kind.ERROR,
						"An error occurred while writing file: " + m.fqcn());
				throw new RuntimeException(e);
			}
		});

		final Optimisation optimisation = configs.isEmpty() ? Optimisation.ALL
				: configs.get(0).optimisation();

		if (optimisation != Optimisation.NONE) {
			try {
				new RetainerMappingModel(this).writeSourceToFile(processingEnv.getFiler(), map,
						roundEnv.getRootElements(), optimisation);
			} catch (IOException e) {
				messager().printMessage(Kind.ERROR, "An error occurred while writing cache class, "
						+ "try adding @RetainConfig(optimisation = Optimisation.NONE) to any class.");
				throw new RuntimeException(e);
			}
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
			utils().getClassArrayFromAnnotationMethod(classes::value)
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
		final List<? extends Element> invalidElements = elements.stream().filter(
				e -> !utils().isAssignable(utils().of(TypeConverter.class), e.asType(), true))
				.collect(Collectors.toList());
		if (!invalidElements.isEmpty()) {
			// we have incorrectly annotated elements
			invalidElements.stream()
					.forEach(e -> messager().printMessage(Kind.ERROR,
							"@DeclaredConverter can only be used on types that implement "
									+ "TypeConverter",
							e));
			return Collections.emptyList();
		} else {
			// good to go
			return invalidElements.stream()
					.map(e -> new DeclaredConverterModel((DeclaredType) e,
							e.getAnnotation(DeclaredConverter.class).value()))
					.collect(Collectors.toList());
		}
	}

	@Override
	public Types types() {
		return processingEnv.getTypeUtils();
	}

	@Override
	public Elements elements() {
		return processingEnv.getElementUtils();
	}

	@Override
	public ProcessorUtils utils() {
		return utils;
	}

	@Override
	public Messager messager() {
		return processingEnv.getMessager();
	}

}
