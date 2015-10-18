package com.sora.util.akatsuki;

import com.google.common.base.Objects;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.FieldSpec.Builder;

public class RetainedField extends Field {

	private boolean skip;
	private Class<? extends TypeConverter<?>> typeConverterClass;

	public RetainedField(Class<?> clazz, Class<?>... parameters) {
		super(clazz, parameters);
	}

	public RetainedField(Class<?> clazz, String name, Class<?>... parameters) {
		super(clazz, name, parameters);
	}

	public RetainedField(Class<?> clazz, String name, String initializer, Class<?>... parameters) {
		super(clazz, name, initializer, parameters);
	}

	public boolean skip() {
		return skip;
	}

	public Class<? extends TypeConverter<?>> typeConverter() {
		return typeConverterClass;
	}

	public RetainedField skip(boolean skip) {
		this.skip = skip;
		return this;
	}

	public RetainedField typeConverter(Class<? extends TypeConverter<?>> typeConverterClass) {
		this.typeConverterClass = typeConverterClass;
		return this;
	}

	@Override
	protected Builder fieldSpecBuilder() {
		final Builder builder = super.fieldSpecBuilder();
		final AnnotationSpec.Builder annotationBuilder = AnnotationSpec.builder(Retained.class);
		if (skip) {
			annotationBuilder.addMember("skip", "$L", true);
		}
		if (typeConverterClass != null) {
			annotationBuilder.addMember("converter", "$T.class", typeConverterClass);
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
		RetainedField that = (RetainedField) o;
		return Objects.equal(skip, that.skip);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(super.hashCode(), skip);
	}
}
