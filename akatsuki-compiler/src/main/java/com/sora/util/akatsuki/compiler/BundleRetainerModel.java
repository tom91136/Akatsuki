package com.sora.util.akatsuki.compiler;

import com.google.common.base.CharMatcher;
import com.google.common.base.MoreObjects;
import com.sora.util.akatsuki.BundleRetainer;
import com.sora.util.akatsuki.DummyTypeConverter;
import com.sora.util.akatsuki.Internal;
import com.sora.util.akatsuki.Retained;
import com.sora.util.akatsuki.TransformationTemplate;
import com.sora.util.akatsuki.TransformationTemplate.Execution;
import com.sora.util.akatsuki.TypeConstraint;
import com.sora.util.akatsuki.TypeFilter;
import com.sora.util.akatsuki.compiler.BundleContext.SimpleBundleContext;
import com.sora.util.akatsuki.compiler.transformations.ArrayTypeAnalyzer;
import com.sora.util.akatsuki.compiler.transformations.CascadingTypeAnalyzer;
import com.sora.util.akatsuki.compiler.transformations.CascadingTypeAnalyzer.Analysis;
import com.sora.util.akatsuki.compiler.transformations.CascadingTypeAnalyzer.InvocationType;
import com.sora.util.akatsuki.compiler.transformations.CollectionTypeAnalyzer;
import com.sora.util.akatsuki.compiler.transformations.ConverterAnalyzer;
import com.sora.util.akatsuki.compiler.transformations.GenericTypeAnalyzer;
import com.sora.util.akatsuki.compiler.transformations.NestedTypeAnalyzer;
import com.sora.util.akatsuki.compiler.transformations.ObjectTypeAnalyzer;
import com.sora.util.akatsuki.compiler.transformations.PrimitiveTypeAnalyzer;
import com.sora.util.akatsuki.compiler.transformations.PrimitiveTypeAnalyzer.Type;
import com.sora.util.akatsuki.compiler.transformations.TemplateAnalyzer;
import com.sora.util.akatsuki.compiler.transformations.TransformationContext;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.MethodSpec.Builder;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

/**
 * Project: Akatsuki Created by tom91136 on 11/07/2015.
 */
public class BundleRetainerModel {

	private static final Set<Modifier> DISALLOWED_MODIFIERS = EnumSet.of(Modifier.FINAL,
			Modifier.STATIC, Modifier.PRIVATE);

	private final String generatedClassName;
	private final String generatedFqpn;
	private final ProcessorContext context;
	private final TypeElement enclosingClass;
	private BundleRetainerModel superModel;

	public final List<ProcessorElement<?>> fields = new ArrayList<>();

	public static class FqcnModelMap extends HashMap<String, BundleRetainerModel> {

	}

	private BundleRetainerModel(ProcessorContext context, TypeElement enclosingClass) {
		this.context = context;
		this.enclosingClass = enclosingClass;
		this.generatedFqpn = context.elements().getPackageOf(enclosingClass).toString();
		this.generatedClassName = createGeneratedClassName();
	}

	private String createGeneratedClassName() {
		final String enclosingName;
		if (enclosingClass.getNestingKind() != NestingKind.TOP_LEVEL) {
			String simpleClassName = enclosingClass.getQualifiedName().toString()
					.replace(generatedFqpn + ".", "");

			enclosingName = CharMatcher.is('.').replaceFrom(simpleClassName, '$');
		} else {
			enclosingName = enclosingClass.getSimpleName().toString();
		}
		return Internal.generateRetainerClassName(enclosingName);
	}

	public TypeElement enclosingClass() {
		return enclosingClass;
	}

	public String fqcn() {
		return generatedFqpn + "." + generatedClassName;
	}

	// create our model here
	public static FqcnModelMap findRetainedFields(ProcessorContext context,
			RoundEnvironment roundEnv) {
		final Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Retained.class);
		FqcnModelMap fqcnMap = new FqcnModelMap();
		int processed = 0;
		boolean verifyOnly = false;
		for (Element element : elements) {

			// skip if error
			if (!annotatedElementValid(context, element) || !enclosingClassValid(context, element)) {
				verifyOnly = true;
				continue;
			}

			// >= 1 error has occurred, we're in verify mode
			if (!verifyOnly) {

				final TypeElement enclosingClass = (TypeElement) element.getEnclosingElement();

				final Retained retained = element.getAnnotation(Retained.class);

				// transient marks the field as skipped
				if (!retained.skip() && !element.getModifiers().contains(Modifier.TRANSIENT)) {
					final BundleRetainerModel model = fqcnMap.computeIfAbsent(
							enclosingClass.getQualifiedName().toString(),
							k -> new BundleRetainerModel(context, enclosingClass));
					final DeclaredType type = context.utils()
							.getClassFromAnnotationMethod(retained::converter);
					model.fields.add(new ProcessorElement(retained, (VariableElement) element, type));
					context.messager().printMessage(Kind.NOTE, "Element marked", element);
				}
			}
			processed++;
		}

		if (processed != elements.size()) {
			context.messager().printMessage(Kind.NOTE,
					(elements.size() - processed)
							+ " error(s) occurred, no files are generated after the first "
							+ "error has occurred.");
		} else {
			// stage 2, mark any fields that got hidden
			fqcnMap.values().stream().forEach(m -> m.processFieldHiding(fqcnMap));
		}

		return verifyOnly ? null : fqcnMap;
	}

	private static boolean annotatedElementValid(ProcessorContext context, Element element) {
		// sanity check
		if (!element.getKind().equals(ElementKind.FIELD)) {
			context.messager().printMessage(Kind.ERROR, "annotated target must be a field",
					element);
			return false;
		}

		// more sanity check
		if (!(element instanceof VariableElement)) {
			context.messager().printMessage(Kind.ERROR, "Element is not a variable?! (should not happen at all) ", element);
			return false;
		}

		// check for invalid modifiers, we can only create classes in the
		// same package
		if (!Collections.disjoint(element.getModifiers(), DISALLOWED_MODIFIERS)) {
			context.messager().printMessage(Kind.ERROR,
					"field with " + DISALLOWED_MODIFIERS.toString() + " cannot be retained",
					element);
			return false;
		}

		return true;
	}

	private static boolean enclosingClassValid(ProcessorContext context, Element element) {
		Element enclosingElement = element.getEnclosingElement();
		while (enclosingElement != null) {
			// skip until we find a class
			if (!enclosingElement.getKind().equals(ElementKind.CLASS))
				break;

			if (!enclosingElement.getKind().equals(ElementKind.CLASS)) {
				context.messager().printMessage(Kind.ERROR,
						"enclosing element(" + enclosingElement.toString() + ") is not a class",
						element);
				return false;
			}

			TypeElement enclosingClass = (TypeElement) enclosingElement;

			// protected, package-private, and public all allow same package
			// access
			if (enclosingClass.getModifiers().contains(Modifier.PRIVATE)) {
				context.messager().printMessage(Kind.ERROR,
						"enclosing class (" + enclosingElement.toString() + ") cannot be private",
						element);
				return false;
			}

			if (enclosingClass.getNestingKind() != NestingKind.TOP_LEVEL
					&& !enclosingClass.getModifiers().contains(Modifier.STATIC)) {
				context.messager().printMessage(Kind.ERROR,
						"enclosing class is nested but not static", element);
				return false;
			}
			enclosingElement = enclosingClass.getEnclosingElement();
		}

		return true;
	}

	public void processFieldHiding(FqcnModelMap map) {
		TypeElement superClass = enclosingClass;
		final TypeMirror objectMirror = context.utils().of(Object.class);
		while ((superClass.getKind() == ElementKind.CLASS && !superClass.equals(objectMirror))) {
			final Element superElement = context.types().asElement(superClass.getSuperclass());
			if (!(superElement instanceof TypeElement)) {
				break;
			}
			superClass = (TypeElement) superElement;
			final BundleRetainerModel model = map.get(superClass.getQualifiedName().toString());
			if (model != null) {
				// we found our closest super class
				if (this.superModel == null)
					this.superModel = model;
				fields.stream().filter(ProcessorElement::notHidden).forEach(field -> {
					final boolean collision = model.fields.stream().anyMatch(
							superField -> superField.fieldName().equals(field.fieldName()));
					field.hidden(collision);
				});
			}
		}
	}

	private static class TypeResolvingContext {
		private final List<TransformationTemplate> templates;
		private final List<DeclaredConverterModel> models;
		private final Map<String, BundleRetainerModel> modelMap;
		private final ProcessorContext context;
		TransformationContext transformationContext = new TransformationContext() {
			@Override
			public Types types() {
				return context.types();
			}

			@Override
			public Elements elements() {
				return context.elements();
			}

			@Override
			public ProcessorUtils utils() {
				return context.utils();
			}

			@Override
			public Messager messager() {
				return context.messager();
			}

			@Override
			public CascadingTypeAnalyzer<?, ? extends TypeMirror, ? extends Analysis> resolve(
					ProcessorElement<?> element) {
				return findTransformationStrategy(element);
			}
		};

		public TypeResolvingContext(List<TransformationTemplate> templates,
				List<DeclaredConverterModel> models, Map<String, BundleRetainerModel> modelMap,
				ProcessorContext context) {
			this.templates = templates;
			this.models = models;
			this.modelMap = modelMap;
			this.context = context;
		}

		private CascadingTypeAnalyzer<?, ? extends TypeMirror, ? extends Analysis> findTransformationStrategy(
				ProcessorElement<?> element) {
			CascadingTypeAnalyzer<?, ?, ?> strategy = null;

			// @Retained defaults to a dummy converter, we don't want that
			final TypeMirror dummyConverter = context.utils().of(DummyTypeConverter.class);
			if (element.typeConverter() != null && !transformationContext.utils()
					.isSameType(element.typeConverter(), dummyConverter, true)) {
				strategy = new ConverterAnalyzer(transformationContext, element.typeConverter());
			}

			if (strategy == null) {
				final DeclaredType converterType = models.stream()
						.filter(m -> testTypeConstraint(element.refinedMirror(), m.filters))
						.findFirst().map(m -> m.converter).orElse(null);
				if (converterType != null)
					strategy = new ConverterAnalyzer(transformationContext, converterType);
			}

			if (strategy == null)
				strategy = findTransformationTemplates(transformationContext, templates, element,
						Execution.BEFORE);

			if (strategy == null) {
				final TypeMirror mirror = element.refinedMirror();
				// TODO consider discarding the switch and move the test
				// condition
				// into
				// every strategy
				if (transformationContext.utils().isPrimitive(mirror)) {
					strategy = new PrimitiveTypeAnalyzer(transformationContext, Type.UNBOXED);
				} else if (transformationContext.utils().isArray(mirror)) {
					strategy = new ArrayTypeAnalyzer(transformationContext);
				} else if (modelMap.containsKey(mirror.toString())) {
					// this field is a type that contains the @Retained
					// annotation
					strategy = new NestedTypeAnalyzer(transformationContext);
				} else if (transformationContext.utils().isAssignable(mirror,
						transformationContext.utils().of(Collection.class), true)) {
					strategy = new CollectionTypeAnalyzer(transformationContext);
				} else if (transformationContext.utils().isAssignable(mirror,
						transformationContext.utils().of(Map.class), true)) {
					// TODO: 7/24/2015 impl
				} else if (transformationContext.utils().isObject(mirror)) {
					strategy = new ObjectTypeAnalyzer(transformationContext);
				} else if (mirror.getKind().equals(TypeKind.TYPEVAR)) {
					// we got a generic type of some bounds
					strategy = new GenericTypeAnalyzer(transformationContext);
				}
			}

			if (strategy == null) {
				final CascadingTypeAnalyzer<?, ?, Analysis> ignored = findTransformationTemplates(
						context, templates, element, Execution.NEVER);
				if (ignored != null)
					context.messager().printMessage(Kind.NOTE,
							"found matching strategy:" + ignored.getClass() + " but ignored");
			}
			return strategy;
		}

		private CascadingTypeAnalyzer<?, ?, Analysis> findTransformationTemplates(
				ProcessorContext context, List<TransformationTemplate> templates,
				ProcessorElement<?> element, Execution execution) {
			return templates.stream().filter(t -> t.execution() == execution)
					.filter(template -> testTypeConstraint(element.refinedMirror(),
							template.filters()))
					.findFirst().map(t -> new TemplateAnalyzer(transformationContext, t))
					.orElse(null);
		}

		private boolean testTypeConstraint(TypeMirror mirror, TypeFilter... filters) {
			return Arrays.stream(filters).anyMatch(filter -> {
				final List<? extends TypeMirror> arguments;
				arguments = mirror instanceof DeclaredType
						? ((DeclaredType) mirror).getTypeArguments() : Collections.emptyList();
				final TypeConstraint[] parameters = filter.parameters();

				// if argument count don't match, short circuit
				if (arguments.size() != parameters.length) {
					return false;
				}

				// check our raw type
				if (!testTypeConstraint(mirror, filter.type()))
					return false;

				for (int i = 0; i < arguments.size(); i++) {
					TypeMirror m = arguments.get(i);
					if (!testTypeConstraint(m, parameters[i])) {
						return false;
					}
				}

				return true;
			});
		}

		private boolean testTypeConstraint(TypeMirror mirror, TypeConstraint constraint) {
			final DeclaredType type = context.utils()
					.getClassFromAnnotationMethod(constraint::type);
			// annotation types are handled differently
			if (context.utils().isAssignable(type, context.utils().of(Annotation.class), true)) {
				// TODO how do we get the element of PrimitiveType? this
				// seems wrong...
				final Element element = (mirror instanceof PrimitiveType)
						? context.types().boxedClass((PrimitiveType) mirror)
						: context.types().asElement(mirror);

				// this happens to array...?
				// TODO figure this out
				if(element == null){
					return false;
				}
				List<? extends AnnotationMirror> annotationMirrors;
				// bounds have different meanings for annotations as
				// they don't have inheritance
				switch (constraint.bound()) {
				case EXACTLY:
					annotationMirrors = element.getAnnotationMirrors();
					break;
				default:
					annotationMirrors = context.elements().getAllAnnotationMirrors(element);
					break;
				}
				if (annotationMirrors.stream().anyMatch(
						m -> context.utils().isSameType(m.getAnnotationType(), type, true)))
					return true;
			}

			switch (constraint.bound()) {
			case EXACTLY:
				if (context.utils().isSameType(type, mirror, true))
					return true;
				break;
			case EXTENDS:
				if (context.utils().isAssignable(type, mirror, true))
					return true;
				break;
			case SUPER:
				// traverse class hierarchy
				TypeMirror superType = mirror;
				while (superType != null && superType.getKind() != TypeKind.NONE) {
					if (context.utils().isSameType(type, superType, true))
						return true;
					superType = ((TypeElement) context.types().asElement(superType))
							.getSuperclass();
				}
				break;
			}
			return false;
		}

	}

	public void writeSourceToFile(Filer filer, List<TransformationTemplate> templates,
			FqcnModelMap map, List<DeclaredConverterModel> models) throws IOException {

		String sourceName = "source";
		String bundleName = "bundle";

		final PackageElement enclosingClassPackage = context.elements()
				.getPackageOf(enclosingClass);

		final SimpleBundleContext bundleContext = new SimpleBundleContext(sourceName, bundleName);

		final ClassName sourceClassName = ClassName.get(enclosingClass);

		TypeVariableName actualClassCapture = TypeVariableName.get("T", sourceClassName);

		final ParameterSpec sourceSpec = ParameterSpec
				.builder(actualClassCapture, sourceName, Modifier.FINAL).build();

		final ParameterSpec bundleSpec = ParameterSpec
				.builder(ClassName.get(AndroidTypes.Bundle.asMirror(context)), bundleName,
						Modifier.FINAL)
				.build();

		final Builder saveMethodBuilder = MethodSpec.methodBuilder("save")
				.addModifiers(Modifier.PUBLIC).returns(void.class).addParameter(sourceSpec)
				.addParameter(bundleSpec);

		final Builder restoreMethodBuilder = MethodSpec.methodBuilder("restore")
				.addModifiers(Modifier.PUBLIC).returns(void.class).addParameter(sourceSpec)
				.addParameter(bundleSpec);

		if (superModel != null) {
			String superInvocation = "super.$L($L, $L)";
			saveMethodBuilder.addStatement(superInvocation, "save", sourceName, bundleName);
			restoreMethodBuilder.addStatement(superInvocation, "restore", sourceName, bundleName);
		}

		final TypeResolvingContext resolvingContext = new TypeResolvingContext(templates, models,
				map, context);

		for (ProcessorElement<?> field : fields) {

			final CascadingTypeAnalyzer<?, ?, ?> strategy = resolvingContext
					.findTransformationStrategy(field);
			if (strategy == null) {
				context.messager().printMessage(Kind.ERROR,
						"unsupported field, reflected type is " + field.refinedMirror()
								+ " representing class is " + field.refinedMirror().getClass(),
						field.originatingElement());
			} else {

				try {
					final Analysis save = strategy.transform(bundleContext, field,
							InvocationType.SAVE);
					final Analysis restore = strategy.transform(bundleContext, field,
							InvocationType.RESTORE);

					saveMethodBuilder.addCode(JavaPoetUtils.escapeStatement(
							save.preEmitOnce() + save.emit() + save.postEmitOnce()));
					restoreMethodBuilder.addCode(JavaPoetUtils.escapeStatement(
							restore.preEmitOnce() + restore.emit() + restore.postEmitOnce()));
				} catch (Exception | Error e) {
					context.messager().printMessage(Kind.ERROR, "an exception occurred: " + e,
							field.originatingElement());
					throw new RuntimeException(e);
				}
			}
		}

		// generate class name here

		TypeSpec.Builder typeSpecBuilder = TypeSpec.classBuilder(generatedClassName)
				.addModifiers(Modifier.PUBLIC).addMethod(saveMethodBuilder.build())
				.addMethod(restoreMethodBuilder.build()).addTypeVariable(actualClassCapture);

		if (superModel != null) {

			final ClassName className = ClassName.get(superModel.generatedFqpn,
					superModel.generatedClassName);

			typeSpecBuilder
					.superclass(ParameterizedTypeName.get(className, TypeVariableName.get("T")));
		} else {
			final ParameterizedTypeName interfaceName = ParameterizedTypeName
					.get(ClassName.get(BundleRetainer.class), TypeVariableName.get("T"));
			typeSpecBuilder.addSuperinterface(interfaceName);
		}

		JavaFile javaFile = JavaFile.builder(generatedFqpn, typeSpecBuilder.build()).build();

		javaFile.writeTo(filer);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("generatedClassName", generatedClassName)
				.add("generatedFqpn", generatedFqpn).add("context", context)
				.add("enclosingClass", enclosingClass).add("superModel", superModel)
				.add("fields", fields).toString();
	}

}
