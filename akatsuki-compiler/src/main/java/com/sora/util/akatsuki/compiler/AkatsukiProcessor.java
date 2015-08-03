package com.sora.util.akatsuki.compiler;

import com.google.auto.service.AutoService;
import com.sora.util.akatsuki.DeclaredConverter;
import com.sora.util.akatsuki.IncludeClasses;
import com.sora.util.akatsuki.Retained;
import com.sora.util.akatsuki.TransformationTemplate;
import com.sora.util.akatsuki.TypeConverter;
import com.sora.util.akatsuki.compiler.BundleRetainerModel.Field;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes({ "com.sora.util.akatsuki.Retained",
		"com.sora.util.akatsuki" + ".TransformationTemplate" })
public class AkatsukiProcessor extends AbstractProcessor implements ProcessorContext {

	private static final boolean DEBUG = false;

	private static final Set<Modifier> DISALLOWED_MODIFIERS = EnumSet.of(Modifier.FINAL,
			Modifier.STATIC, Modifier.PRIVATE);

	private ProcessorUtils utils;

	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		this.utils = new ProcessorUtils(processingEnv.getTypeUtils(),
				processingEnv.getElementUtils());
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

		final List<TransformationTemplate> templates = findTransformationTemplates(roundEnv);
		final List<DeclaredConverterModel> declaredConverters = findDeclaredConverters(roundEnv);
		final HashMap<String, BundleRetainerModel> map = findRetainedFields(roundEnv);

		if (map == null) {
			messager().printMessage(Kind.ERROR, "verification failed");
			return true;
		}

		map.values().stream().forEach(m -> {
			messager().printMessage(Kind.OTHER, m.toString());

			try {
				m.writeSourceToFile(processingEnv.getFiler(), templates,map,  declaredConverters);
			} catch (IOException e) {
				e.printStackTrace();
			}
		});

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
		final Map<Boolean, List<Element>> partitioned = elements.stream()
				.collect(Collectors.partitioningBy(e -> utils()
						.isAssignable(utils().of(TypeConverter.class), e.asType(), true)));

		if (partitioned.containsKey(false)) {
			// we have incorrectly annotated elements
			partitioned.get(false)
					.forEach(e -> messager().printMessage(Kind.ERROR,
							"@DeclaredConverter can only be used on types that implement TypeConverter",
							e));
			return Collections.emptyList();
		} else {
			// good to go
			return partitioned.getOrDefault(true, Collections.emptyList()).stream()
					.map(e -> new DeclaredConverterModel((DeclaredType) e,
							e.getAnnotation(DeclaredConverter.class).value()))
					.collect(Collectors.toList());
		}
	}

	// create our model here
	private HashMap<String, BundleRetainerModel> findRetainedFields(RoundEnvironment roundEnv) {
		final Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Retained.class);
		HashMap<String, BundleRetainerModel> fqcnMap = new HashMap<>();

		int processed = 0;
		boolean verifyOnly = false;
		for (Element field : elements) {

			// skip if error
			if (!annotatedElementValid(field) || !enclosingClassValid(field)) {
				verifyOnly = true;
				continue;
			}

			// >= 1 error has occurred, we're in verify mode
			if (!verifyOnly) {

				final TypeElement enclosingClass = (TypeElement) field.getEnclosingElement();

				final Retained retained = field.getAnnotation(Retained.class);

				// transient marks the field as skipped
				if (!retained.skip() && !field.getModifiers().contains(Modifier.TRANSIENT)) {
					final BundleRetainerModel model = fqcnMap.computeIfAbsent(
							enclosingClass.getQualifiedName().toString(),
							k -> new BundleRetainerModel(this, enclosingClass));
					final DeclaredType type = utils()
							.getClassFromAnnotationMethod(retained::converter);
					model.fields.add(new Field((VariableElement) field, type));
				}
			}
			processed++;
		}

		if (processed != elements.size()) {
			messager().printMessage(Kind.NOTE,
					(elements.size() - processed)
							+ " error(s) occurred, no files are generated after the first "
							+ "error has occurred.");
		} else {
			// stage 2, mark any fields that got hidden
			fqcnMap.values().stream().forEach(m -> m.processFieldHiding(fqcnMap));
		}

		return verifyOnly ? null : fqcnMap;
	}

	private boolean annotatedElementValid(Element element) {
		// sanity check
		if (!element.getKind().equals(ElementKind.FIELD)) {
			messager().printMessage(Kind.ERROR, "annotated target must be a field", element);
			return false;
		}

		// check for invalid modifiers, we can only create classes in the
		// same package
		if (!Collections.disjoint(element.getModifiers(), DISALLOWED_MODIFIERS)) {
			messager().printMessage(Kind.ERROR,
					"field with " + DISALLOWED_MODIFIERS.toString() + " cannot be retained",
					element);
			return false;
		}
		return true;
	}

	private boolean enclosingClassValid(Element element) {
		int depth = 0;
		Element enclosingElement = element.getEnclosingElement();
		while (enclosingElement != null) {
			if (DEBUG)
				messager().printMessage(Kind.NOTE, "scanning package tree, depth = " + depth,
						enclosingElement);

			// skip until we find a class
			if (!enclosingElement.getKind().equals(ElementKind.CLASS))
				break;

			if (!enclosingElement.getKind().equals(ElementKind.CLASS)) {
				messager().printMessage(Kind.ERROR,
						"enclosing element(" + enclosingElement.toString() + ") is not a class",
						element);
				return false;
			}

			TypeElement enclosingClass = (TypeElement) enclosingElement;

			// protected, package-private, and public all allow same package
			// access
			if (enclosingClass.getModifiers().contains(Modifier.PRIVATE)) {
				messager().printMessage(Kind.ERROR,
						"enclosing class (" + enclosingElement.toString() + ") cannot be private",
						element);
				return false;
			}

			if (enclosingClass.getNestingKind() != NestingKind.TOP_LEVEL
					&& !enclosingClass.getModifiers().contains(Modifier.STATIC)) {
				messager().printMessage(Kind.ERROR, "enclosing class is nested but not static",
						element);
				return false;
			}

			depth++;
			enclosingElement = enclosingClass.getEnclosingElement();
		}

		return true;
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
