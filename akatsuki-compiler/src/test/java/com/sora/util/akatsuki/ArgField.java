package com.sora.util.akatsuki;

import com.google.common.base.Objects;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.FieldSpec.Builder;

public class ArgField extends Field {

	private boolean skip;

	private boolean optional;

	public ArgField(Class<?> clazz, Class<?>... parameters) {
		super(clazz, parameters);
	}

	public ArgField(Class<?> clazz, String name, Class<?>... parameters) {
		super(clazz, name, parameters);
	}

	public ArgField(Class<?> clazz, String name, String initializer, Class<?>... parameters) {
		super(clazz, name, initializer, parameters);
	}

	public boolean skip() {
		return skip;
	}

	public ArgField skip(boolean skip) {
		this.skip = skip;
		return this;
	}

	public boolean optional() {
		return optional;
	}

	public ArgField optional(boolean optional) {
		this.optional = optional;
		return this;
	}

	@Override
	protected Builder fieldSpecBuilder() {
		final Builder builder = super.fieldSpecBuilder();
		final AnnotationSpec.Builder annotationBuilder = AnnotationSpec.builder(Arg.class);
		if (skip) {
			annotationBuilder.addMember("skip", "$L", true);
		}

		if (optional) {
			annotationBuilder.addMember("optional", "$L", true);
		}
		builder.addAnnotation(annotationBuilder.build());
		return builder;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		if (!super.equals(o))
			return false;
		ArgField argField = (ArgField) o;
		return Objects.equal(skip, argField.skip) && Objects.equal(optional, argField.optional);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(super.hashCode(), skip, optional);
	}
}
