package com.sora.util.akatsuki.models;

import static com.sora.util.akatsuki.models.SourceTreeModel.enclosingClassValid;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleElementVisitor8;

import com.google.common.base.CharMatcher;
import com.google.common.base.MoreObjects;
import com.sora.util.akatsuki.ProcessorContext;
import com.sora.util.akatsuki.models.FieldModel.Flag;

public class SourceClassModel extends BaseModel {

	private final TypeElement enclosingClass;
	private SourceClassModel parent;
	private Set<SourceClassModel> children;
	final List<FieldModel> fields = new ArrayList<>();

	SourceClassModel(ProcessorContext context, TypeElement enclosingClass) {
		super(context);
		this.enclosingClass = enclosingClass;

		if (!enclosingClassValid(context, enclosingClass)) {
			throw new RuntimeException("Element " + enclosingClass + " is not valid");
		}

	}

	void linkParent(Map<String, SourceClassModel> map) {
		TypeElement superClass = enclosingClass;
		while ((superClass.getKind() == ElementKind.CLASS)) {
			final Element superElement = context.types().asElement(superClass.getSuperclass());
			if (!(superElement instanceof TypeElement)) {
				break;
			}
			superClass = (TypeElement) superElement;

			final SourceClassModel superModel = map.get(superClass.getQualifiedName().toString());
			if (superModel != null) {
				// we found our closest super class
				this.parent = superModel;
				break;
			}
		}
		// this.children.forEach(c -> c.linkParent(map));
	}

	void findChildren(Map<String, SourceClassModel> map, RoundEnvironment environment) {
		Set<SourceClassModel> models = new HashSet<>();
		for (Element root : environment.getRootElements()) {

			root.accept(new SimpleElementVisitor8<Void, Set<SourceClassModel>>() {

				@Override
				public Void visitType(TypeElement element, Set<SourceClassModel> models) {
					if (element.getKind() == ElementKind.CLASS && element != enclosingClass) {
						// we're in a class now
						if (context.utils().isAssignable(element.asType(), mirror(), true)) {
							// the class extends the source class, add it to the
							// map
							SourceClassModel existing = map
									.get(element.getQualifiedName().toString());
							if (existing != null) {
								// class model was instantiated in the
								// verification phase

								if (existing.parent == null)
									existing.parent = SourceClassModel.this;

								models.add(existing);
							} else {
								// new class found
								SourceClassModel model = new SourceClassModel(context, element);
								model.parent = SourceClassModel.this;
								models.add(model);
								model.findChildren(map, environment);
							}
						}
						// keep walking
						element.getEnclosedElements().forEach(e -> e.accept(this, models));
					}
					return null;
				}
			}, models);
		}
		this.children = Collections.unmodifiableSet(models);
	}

	void markHiddenFields() {
		// set colliding fields with the proper flag
		fields.stream().filter(model -> parent != null && parent.containsFieldWithSameName(model))
				.findAny().ifPresent(m -> m.flags.add(Flag.HIDDEN));
	}

	private boolean containsFieldWithSameName(FieldModel model) {
		return fields.stream().anyMatch(m -> m.name().equals(model.name()));
	}

	public List<FieldModel> fields() {
		return Collections.unmodifiableList(fields);
	}

	public String fullyQualifiedName() {
		return enclosingClass.getQualifiedName().toString();
	}

	public String simpleName() {
//		if (enclosingClass.getNestingKind() != NestingKind.TOP_LEVEL) {
//			String fqn = enclosingClass.getQualifiedName().toString();
//			return fqn.substring(fqn.indexOf('.') + 1, fqn.length());
//		} else {
			return enclosingClass.getSimpleName().toString();
//		}
	}

	public String fullyQualifiedPackageName() {
		return context.elements().getPackageOf(enclosingClass).toString();
	}

	public boolean containsModifier(Modifier modifier) {
		return enclosingClass.getModifiers().contains(modifier);
	}

	public ClassInfo asClassInfo() {
		String fqpn = fullyQualifiedPackageName();
		String className;
		if (enclosingClass.getNestingKind() != NestingKind.TOP_LEVEL) {
			// in case of the static class, we get all the nested classes and
			// replace '.' with '$'
			className = CharMatcher.is('.')
					.replaceFrom(fullyQualifiedName().replace(fqpn + ".", ""), '$');
		} else {
			className = simpleName();
		}
		return new ClassInfo(fqpn, className);
	}

	public TypeElement originatingElement() {
		return enclosingClass;
	}

	public <A extends Annotation> Optional<A> annotation(Class<A> annotationClass) {
		return Optional.ofNullable(enclosingClass.getAnnotation(annotationClass));
	}

	public Optional<SourceClassModel> directSuperModel() {
		return Optional.ofNullable(parent);
	}

	public Optional<SourceClassModel> directSuperModelWithAnnotation(
			Class<? extends Annotation> annotationClass) {
		return parent != null && parent.containsAnyAnnotation(annotationClass) ? directSuperModel()
				: Optional.empty();
	}

	public boolean containsAnyAnnotation(Class<? extends Annotation> annotationClass) {
		return fields.stream().anyMatch(fm -> fm.annotation(annotationClass).isPresent());
	}

	public Set<SourceClassModel> children() {
		return children;
	}

	public TypeMirror mirror() {
		return enclosingClass.asType();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		SourceClassModel that = (SourceClassModel) o;
		return enclosingClass.equals(that.enclosingClass);

	}

	@Override
	public int hashCode() {
		return enclosingClass.hashCode();
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("enclosingClass", enclosingClass)
				.add("parent", parent != null ? parent.fullyQualifiedName() : null)
				.add("children", Arrays.toString(children.stream()
						.map(SourceClassModel::fullyQualifiedName).toArray(String[]::new)))
				.add("fields", fields).toString();
	}
}
