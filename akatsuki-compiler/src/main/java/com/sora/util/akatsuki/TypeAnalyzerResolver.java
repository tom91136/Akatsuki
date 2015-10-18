package com.sora.util.akatsuki;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

import com.sora.util.akatsuki.TransformationTemplate.Execution;
import com.sora.util.akatsuki.TypeConverter.DummyTypeConverter;
import com.sora.util.akatsuki.analyzers.ArrayTypeAnalyzer;
import com.sora.util.akatsuki.analyzers.CascadingTypeAnalyzer;
import com.sora.util.akatsuki.analyzers.CascadingTypeAnalyzer.Analysis;
import com.sora.util.akatsuki.analyzers.CollectionTypeAnalyzer;
import com.sora.util.akatsuki.analyzers.ConverterAnalyzer;
import com.sora.util.akatsuki.analyzers.Element;
import com.sora.util.akatsuki.analyzers.GenericTypeAnalyzer;
import com.sora.util.akatsuki.analyzers.NestedTypeAnalyzer;
import com.sora.util.akatsuki.analyzers.ObjectTypeAnalyzer;
import com.sora.util.akatsuki.analyzers.PrimitiveTypeAnalyzer;
import com.sora.util.akatsuki.analyzers.PrimitiveTypeAnalyzer.Type;
import com.sora.util.akatsuki.analyzers.TemplateAnalyzer;
import com.sora.util.akatsuki.models.SourceClassModel;
import com.sora.util.akatsuki.models.SourceTreeModel;

public class TypeAnalyzerResolver {
	private final List<TransformationTemplate> templates;
	private final List<DeclaredConverterModel> models;
	private final SourceTreeModel treeModel;
	private final ProcessorContext context;
	private final TypeMirror dummyConverter;
	private final TransformationContext transformationContext;

	public TypeAnalyzerResolver(List<TransformationTemplate> templates,
			List<DeclaredConverterModel> models, SourceTreeModel treeModel,
			ProcessorContext context) {
		this.templates = templates;
		this.models = models;
		this.treeModel = treeModel;
		this.context = context;
		this.dummyConverter = context.utils().of(DummyTypeConverter.class);
		this.transformationContext = new TransformationContext(context, this);
	}

	CascadingTypeAnalyzer<?, ? extends TypeMirror, ? extends Analysis> resolve(Element<?> element) {
		CascadingTypeAnalyzer<?, ?, ?> strategy;
		final TypeMirror mirror = element.refinedMirror();

		strategy = element.model().annotation(With.class).map(with -> {
			DeclaredType declaredType = context.utils().getClassFromAnnotationMethod(with::value);
			// @With defaults to a dummy converter, we don't want that
			if (!context.utils().isSameType(declaredType, dummyConverter, true)) {
				return new ConverterAnalyzer(transformationContext, declaredType);
			}
			return null;
		}).orElse(null);

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
			SourceClassModel model = treeModel.findModelWithAssignableMirror(mirror);
			// this field is a type that contains the @Retained
			// annotation
			if (model != null) {
				Log.verbose(context, "Nested element found", element.originatingElement());
				strategy = new NestedTypeAnalyzer(transformationContext);
			}

		}

		if (strategy == null) {

			// TODO consider discarding the switch and move the test
			// condition
			// into
			// every strategy
			if (transformationContext.utils().isPrimitive(mirror)) {
				strategy = new PrimitiveTypeAnalyzer(transformationContext, Type.UNBOXED);
			} else if (transformationContext.utils().isArray(mirror)) {
				strategy = new ArrayTypeAnalyzer(transformationContext);
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
			List<TransformationTemplate> templates, Element<?> element, Execution execution) {
		return templates.stream().filter(t -> t.execution() == execution)
				.filter(template -> testTypeFilter(element.refinedMirror(), template.filters()))
				.findFirst().map(t -> new TemplateAnalyzer(transformationContext, t)).orElse(null);
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
			final javax.lang.model.element.Element element = (mirror instanceof PrimitiveType)
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
			boolean clazz = type.asElement().getKind() == ElementKind.CLASS;
			// classes and interfaces have inverted schematics for isAssignable
			// for some reason...
			if (context.utils().isAssignable(clazz ? type : mirror, clazz ? mirror : type, true))
				return true;
			break;

		case SUPER:
			// swap argument because we are checking whether the mirror is a
			// super type of type
			if (context.utils().isAssignable(mirror, type, true))
				return true;
			break;
		}
		return false;
	}

}
