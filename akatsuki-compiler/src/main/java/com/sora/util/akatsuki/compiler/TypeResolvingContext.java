package com.sora.util.akatsuki.compiler;

import com.sora.util.akatsuki.DummyTypeConverter;
import com.sora.util.akatsuki.TransformationTemplate;
import com.sora.util.akatsuki.TransformationTemplate.Execution;
import com.sora.util.akatsuki.TypeConstraint;
import com.sora.util.akatsuki.TypeFilter;
import com.sora.util.akatsuki.compiler.BundleRetainerModel.FqcnModelMap;
import com.sora.util.akatsuki.compiler.transformations.ArrayTypeAnalyzer;
import com.sora.util.akatsuki.compiler.transformations.CascadingTypeAnalyzer;
import com.sora.util.akatsuki.compiler.transformations.CascadingTypeAnalyzer.Analysis;
import com.sora.util.akatsuki.compiler.transformations.CollectionTypeAnalyzer;
import com.sora.util.akatsuki.compiler.transformations.ConverterAnalyzer;
import com.sora.util.akatsuki.compiler.transformations.GenericTypeAnalyzer;
import com.sora.util.akatsuki.compiler.transformations.NestedTypeAnalyzer;
import com.sora.util.akatsuki.compiler.transformations.ObjectTypeAnalyzer;
import com.sora.util.akatsuki.compiler.transformations.PrimitiveTypeAnalyzer;
import com.sora.util.akatsuki.compiler.transformations.PrimitiveTypeAnalyzer.Type;
import com.sora.util.akatsuki.compiler.transformations.TemplateAnalyzer;
import com.sora.util.akatsuki.compiler.transformations.TransformationContext;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

class TypeResolvingContext {
	private final List<TransformationTemplate> templates;
	private final List<DeclaredConverterModel> models;
	private final FqcnModelMap modelMap;
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
			List<DeclaredConverterModel> models, FqcnModelMap modelMap, ProcessorContext context) {
		this.templates = templates;
		this.models = models;
		this.modelMap = modelMap;
		this.context = context;
	}

	CascadingTypeAnalyzer<?, ? extends TypeMirror, ? extends Analysis> findTransformationStrategy(
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
					.filter(m -> testTypeFilter(element.refinedMirror(), m.filters)).findFirst()
					.map(m -> m.converter).orElse(null);
			if (converterType != null)
				strategy = new ConverterAnalyzer(transformationContext, converterType);
		}

		if (strategy == null)
			strategy = findTransformationTemplates(templates, element, Execution.BEFORE);

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
			} else if (testTypeAssignableFromModel(mirror)) {
				// this field is a type that contains the @Retained
				// annotation
				strategy = new NestedTypeAnalyzer(transformationContext);
			} else if (transformationContext.utils().isAssignable(mirror,
					transformationContext.utils().of(Collection.class), true)) {
				strategy = new CollectionTypeAnalyzer(transformationContext);
			} else if (transformationContext.utils().isAssignable(mirror,
					transformationContext.utils().of(Map.class), true)) {
				// TODO: ETS phase 2
			} else if (transformationContext.utils().isObject(mirror)) {
				strategy = new ObjectTypeAnalyzer(transformationContext);
			} else if (mirror.getKind().equals(TypeKind.TYPEVAR)) {
				// we got a generic type of some bounds
				strategy = new GenericTypeAnalyzer(transformationContext);
			}
		}

		if (strategy == null) {
			final CascadingTypeAnalyzer<?, ?, Analysis> ignored = findTransformationTemplates(
					templates, element, Execution.NEVER);
			if (ignored != null)
				context.messager().printMessage(Kind.NOTE,
						"found matching strategy:" + ignored.getClass() + " but ignored");
		}
		return strategy;
	}

	private CascadingTypeAnalyzer<?, ?, Analysis> findTransformationTemplates(
			List<TransformationTemplate> templates, ProcessorElement<?> element,
			Execution execution) {
		return templates.stream().filter(t -> t.execution() == execution)
				.filter(template -> testTypeFilter(element.refinedMirror(), template.filters()))
				.findFirst().map(t -> new TemplateAnalyzer(transformationContext, t)).orElse(null);
	}

	private boolean testTypeAssignableFromModel(TypeMirror mirror) {
		if (modelMap.containsKey(mirror.toString())) {
			return true;
		}
		List<TypeMirror> mirrors = modelMap.values().stream().map(m -> m.enclosingClass().asType())
				.collect(Collectors.toList());
		return transformationContext.utils().isAssignable(mirror, true,
				mirrors.toArray(new TypeMirror[mirrors.size()]));
	}

	private boolean testTypeFilter(TypeMirror mirror, TypeFilter... filters) {
		return Arrays.stream(filters).anyMatch(filter -> {
			final List<? extends TypeMirror> arguments;
			arguments = mirror instanceof DeclaredType ? ((DeclaredType) mirror).getTypeArguments()
					: Collections.emptyList();
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
		final DeclaredType type = context.utils().getClassFromAnnotationMethod(constraint::type);
		// annotation types are handled differently
		if (context.utils().isAssignable(type, context.utils().of(Annotation.class), true)) {
			// TODO how do we get the element of PrimitiveType? this
			// seems wrong...
			final Element element = (mirror instanceof PrimitiveType)
					? context.types().boxedClass((PrimitiveType) mirror)
					: context.types().asElement(mirror);

			// this happens to array...?
			// TODO figure this out
			if (element == null) {
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
			if (annotationMirrors.stream()
					.anyMatch(m -> context.utils().isSameType(m.getAnnotationType(), type, true)))
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
				superType = ((TypeElement) context.types().asElement(superType)).getSuperclass();
			}
			break;
		}
		return false;
	}

}
