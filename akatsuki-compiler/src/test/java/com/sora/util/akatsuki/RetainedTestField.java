package com.sora.util.akatsuki;

import com.google.common.base.Objects;
import com.sora.util.akatsuki.Retained.RestorePolicy;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.FieldSpec.Builder;

public class RetainedTestField extends TestField {

	private boolean skip;
	private RestorePolicy policy;

	public RetainedTestField(Class<?> clazz, Class<?>... parameters) {
		super(clazz, parameters);
	}

	public RetainedTestField(Class<?> clazz, String name, Class<?>... parameters) {
		super(clazz, name, parameters);
	}

	public RetainedTestField(Class<?> clazz, String name, String initializer,
			Class<?>... parameters) {
		super(clazz, name, initializer, parameters);
	}

	public boolean skip() {
		return skip;
	}

	public RetainedTestField skip(boolean skip) {
		this.skip = skip;
		return this;
	}

	public RestorePolicy policy() {
		return policy;
	}

	public RetainedTestField policy(RestorePolicy policy) {
		this.policy = policy;
		return this;
	}

	@Override
	protected Builder fieldSpecBuilder() {
		final Builder builder = super.fieldSpecBuilder();
		final AnnotationSpec.Builder annotationBuilder = AnnotationSpec.builder(Retained.class);
		if (skip) {
			annotationBuilder.addMember("skip", "$L", true);
		}
		if (policy != null) {
			annotationBuilder.addMember("restorePolicy", "$T.$L", RestorePolicy.class, policy);
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
		RetainedTestField that = (RetainedTestField) o;
		return Objects.equal(skip, that.skip) && Objects.equal(policy, that.policy);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(super.hashCode(), skip, policy);
	}
}
