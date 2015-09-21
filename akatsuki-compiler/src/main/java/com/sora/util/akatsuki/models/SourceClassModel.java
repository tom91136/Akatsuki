package com.sora.util.akatsuki.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import com.google.common.base.CharMatcher;
import com.google.common.base.MoreObjects;
import com.sora.util.akatsuki.ProcessorContext;
import com.sora.util.akatsuki.models.FieldModel.Flag;

public class SourceClassModel extends BaseModel {

	private final TypeElement enclosingClass;
	private SourceClassModel directSuperModel;
	final List<FieldModel> fields = new ArrayList<>();

	SourceClassModel(ProcessorContext context, TypeElement enclosingClass) {
		super(context);
		this.enclosingClass = enclosingClass;
	}

	void findSuperClass(Map<String, SourceClassModel> map) {
		TypeElement superClass = enclosingClass;
		final TypeMirror objectMirror = context.utils().of(Object.class);
		while ((superClass.getKind() == ElementKind.CLASS && !superClass.equals(objectMirror))) {
			final Element superElement = context.types().asElement(superClass.getSuperclass());
			if (!(superElement instanceof TypeElement)) {
				break;
			}
			superClass = (TypeElement) superElement;
			final SourceClassModel superModel = map.get(superClass.getQualifiedName().toString());
			if (superModel != null) {
				// we found our closest super class
				this.directSuperModel = superModel;
				break;
			}
		}
	}

	void markHiddenFields() {
		// set colliding fields with the proper flag
		fields.stream()
				.filter(model -> directSuperModel != null
						&& directSuperModel.containsFieldWithSameName(model))
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
		return enclosingClass.getSimpleName().toString();
	}

	public String fullyQualifiedPackageName() {
		return context.elements().getPackageOf(enclosingClass).toString();
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

	public SourceClassModel directSuperModel() {
		return directSuperModel;
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
				.add("directSuperModel", directSuperModel).add("fields", fields).toString();
	}
}
