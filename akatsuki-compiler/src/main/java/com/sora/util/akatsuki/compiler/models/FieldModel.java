package com.sora.util.akatsuki.compiler.models;

import java.lang.annotation.Annotation;
import java.util.EnumSet;
import java.util.Set;

import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.google.common.base.MoreObjects;

public class FieldModel {

	public enum Flag {
		HIDDEN
	}

	public final VariableElement element;
	private final Set<Class<? extends Annotation>> annotated;
	final EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);

	public FieldModel(VariableElement element, Set<Class<? extends Annotation>> annotated) {
		this.element = element;
		this.annotated = annotated;
	}

	public String name() {
		return element.getSimpleName().toString();
	}

	public <A extends Annotation> A annotation(Class<A> annotationClass) {
		if (hasAnnotation(annotationClass)) return element.getAnnotation(annotationClass);
		else return null;
	}

	public boolean hasAnnotation(Class<? extends Annotation> annotationClass) {
		return annotated.contains(annotationClass);
	}

	public EnumSet<Flag> flags() {
		return EnumSet.copyOf(flags);
	}

	public TypeElement enclosingElement() {
		return (TypeElement) element.getEnclosingElement();
	}

	public TypeMirror type() {
		return element.asType();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		FieldModel that = (FieldModel) o;
		return element.equals(that.element);

	}

	@Override
	public int hashCode() {
		return element.hashCode();
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("element", element).add("flags", flags)
				       .toString();
	}
}
