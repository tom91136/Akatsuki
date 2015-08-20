package com.sora.util.akatsuki.compiler;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.sora.util.akatsuki.Retained;
import com.sora.util.akatsuki.TypeConverter;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.FieldSpec.Builder;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

import java.util.Arrays;
import java.util.stream.Collectors;

public class Field {
	public final Class<?> clazz;
	public final Class<?>[] parameters;
	public final String name;
	public final String initializer;

	public Field(Class<?> clazz, Class<?>... parameters) {
		this.clazz = clazz;
		this.parameters = parameters;
		this.name = "_" + createName(clazz, parameters);
		this.initializer = null;
	}

	public Field(Class<?> clazz, String name, Class<?>... parameters) {
		this.clazz = clazz;
		this.parameters = parameters;
		this.name = name;
		this.initializer = null;
	}

	public Field(Class<?> clazz, String name, String initializer, Class<?>... parameters) {
		this.clazz = clazz;
		this.parameters = parameters;
		this.name = name;
		this.initializer = initializer;
	}

	private static String createName(Class<?> clazz, Class<?>... parameters) {
		if (clazz.isArray()) {
			return createName(clazz.getComponentType(), parameters) + "Array";
		} else {
			return parameters.length == 0 ? clazz.getSimpleName()
					: Arrays.stream(parameters).map(p -> createName(p))
							.collect(Collectors.joining()) + createName(clazz);
		}
	}

	public boolean generic() {
		return parameters.length > 0;
	}

	public TypeName typeName() {
		if (generic()) {
			Class<?> rawType = clazz.isArray() ? clazz.getComponentType() : clazz;
			final ParameterizedTypeName typeName = ParameterizedTypeName.get(rawType, parameters);
			return clazz.isArray() ? ArrayTypeName.of(typeName) : typeName;
		} else {
			return TypeName.get(clazz);
		}
	}

	protected Builder fieldSpecBuilder() {
		final Builder builder = FieldSpec.builder(typeName(), name);
		if (initializer != null)
			builder.initializer(initializer);
		return builder;
	}

	public final FieldSpec createFieldSpec() {
		return fieldSpecBuilder().build();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		Field field = (Field) o;
		return Objects.equal(clazz, field.clazz) && Objects.equal(parameters, field.parameters)
				&& Objects.equal(name, field.name);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(clazz, parameters, name);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("clazz", clazz).add("parameters", parameters)
				.add("name", name).toString();
	}

	public static class RetainedField extends Field {

		private boolean skip;
		private Class<? extends TypeConverter<?>> typeConverterClass;

		public RetainedField(Class<?> clazz, Class<?>... parameters) {
			super(clazz, parameters);
		}

		public RetainedField(Class<?> clazz, String name, Class<?>... parameters) {
			super(clazz, name, parameters);
		}

		public RetainedField(Class<?> clazz, String name, String initializer,
				Class<?>... parameters) {
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

}
