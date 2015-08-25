package com.sora.util.akatsuki.compiler;

import android.os.Bundle;

import com.sora.util.akatsuki.DeclaredConverter;
import com.sora.util.akatsuki.Retained;
import com.sora.util.akatsuki.TransformationTemplate;
import com.sora.util.akatsuki.TransformationTemplate.Execution;
import com.sora.util.akatsuki.TransformationTemplate.StatementTemplate;
import com.sora.util.akatsuki.TypeConstraint;
import com.sora.util.akatsuki.TypeConstraint.Bound;
import com.sora.util.akatsuki.TypeConverter;
import com.sora.util.akatsuki.TypeFilter;
import com.sora.util.akatsuki.compiler.Field.RetainedField;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.FieldSpec.Builder;

import org.junit.Ignore;
import org.junit.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.lang.model.element.Modifier;

public class TransformationTemplateTest extends CodeGenerationTestBase {

	@Test
	public void testClassConstraint() {
		testTransformationTemplate(Bound.EXACTLY, StringObject.class, StringObject.class);
	}

	@Test(expected = RuntimeException.class)
	public void testInvalidClassConstraint() {
		// should not match anything and fail to compile
		testTransformationTemplate(Bound.EXACTLY, StringObject.class, BaseStringObject.class);
	}

	@Test
	public void testSubClassConstraint() {
		testTransformationTemplate(Bound.EXTENDS, StringObject.class, InheritedStringObject.class);
	}

	@Test
	public void testSuperClassConstraint() {
		testTransformationTemplate(Bound.SUPER, StringObject.class, BaseStringObject.class);
	}

	@Test
	public void testAnnotationConstraint() {
		testTransformationTemplate(Bound.EXACTLY, StringObject.class, RandomAnnotation.class);
	}

	@Test
	public void testInheritedAnnotationConstraint() {
		testTransformationTemplate(Bound.EXTENDS, InheritedStringObject.class,
				RandomAnnotation.class);
		testTransformationTemplate(Bound.SUPER, InheritedStringObject.class,
				RandomAnnotation.class);
	}

	@Test
	public void testMixedConstraint() {
		testTransformationTemplate(Bound.EXACTLY, StringObject.class, StringObject.class,
				RandomAnnotation.class);

	}


	// leave unimplemented for now
//	@Test
//	public void testCompoundType() {
//		final JavaSource testClass = new JavaSource(TEST_PACKAGE, generateClassName(),
//				Modifier.PUBLIC).fields(new RetainedField(List.class, "a",
//						"new ArrayList<>()", StringObject.class)
//								.createFieldSpec());
//		testClass.builderTransformer(
//				(builder, source) -> builder.addAnnotation(createTransformationTemplate(
//						Bound.EXACTLY, StringObject.class, RandomAnnotation.class)));
//
//		final TestEnvironment environment = new TestEnvironment(this, testClass);
//
//		environment.invokeSaveAndRestore();
//		environment.testSaveRestoreInvocation(n -> true, TestEnvironment.CLASS,
//				Collections.singleton(new RetainedField(List.class, "a", StringObject.class)));
//
//	}

	@Test
	public void testTypeConverter() {
		testTypeConverter(false);
	}

	@Test
	public void testRegisteredTypeConverter() {
		testTypeConverter(true);
	}

	protected AnnotationSpec createTransformationTemplate(Bound bound, Class<?> staticClass,
			Class<?>... constraints) {
		final String objectFqcn = staticClass.getCanonicalName();
		AnnotationSpec save = AnnotationSpec
				.builder(StatementTemplate.class).addMember("value",
						"\"{{bundle}}.putString({{keyName}}, $L.wrap({{fieldName}}))\"", objectFqcn)
				.build();

		AnnotationSpec restore = AnnotationSpec.builder(StatementTemplate.class)
				.addMember("type", "$T.$L", StatementTemplate.Type.class,
						StatementTemplate.Type.ASSIGNMENT)
				.addMember("value", "\"$L.unwrap({{bundle}}.getString({{keyName}}))\"", objectFqcn)
				.addMember("variable", "\"{{fieldName}}\"").build();

		final AnnotationSpec.Builder annotationSpec = AnnotationSpec
				.builder(TransformationTemplate.class).addMember("save", "$L", save)
				.addMember("restore", "$L", restore)
				.addMember("execution", "$T.$L", Execution.class, Execution.BEFORE);

		for (Class<?> constraint : constraints) {
			final AnnotationSpec constraintsSpec = AnnotationSpec.builder(TypeConstraint.class)
					.addMember("type", "$T.class", constraint)
					.addMember("bound", "$T.$L", Bound.class, bound).build();

			AnnotationSpec.Builder filterSpec = AnnotationSpec.builder(TypeFilter.class);
			filterSpec.addMember("type", "$L", constraintsSpec);

			annotationSpec.addMember("filters", "$L", filterSpec.build());

		}
		return annotationSpec.build();
	}

	protected void testTransformationTemplate(Bound bound, Class<?> staticClass,
			Class<?>... constraints) {
		final String objectFqcn = staticClass.getCanonicalName();

		final JavaSource testClass = new JavaSource(TEST_PACKAGE, generateClassName(),
				Modifier.PUBLIC).fields(
						new RetainedField(StringObject.class, "a", "new " + objectFqcn + "(\"A\")")
								.createFieldSpec());
		testClass.builderTransformer((builder, source) -> builder
				.addAnnotation(createTransformationTemplate(bound, staticClass, constraints)));

		final TestEnvironment environment = new TestEnvironment(this, testClass);

		environment.invokeSaveAndRestore();
		environment.testSaveRestoreInvocation(n -> true, TestEnvironment.CLASS,
				Collections.singleton(new RetainedField(String.class, "a")));
	}

	protected void testTypeConverter(boolean registered) {
		final Field retainedField = new Field(StringObject.class, "a",
				"new " + StringObject.class.getCanonicalName() + "(\"A\")");
		final Builder fieldBuilder = retainedField.fieldSpecBuilder();
		final ArrayList<JavaSource> sources = new ArrayList<>();
		if (registered) {
			fieldBuilder.addAnnotation(Retained.class);
			final AnnotationSpec constraintSpec = AnnotationSpec.builder(TypeConstraint.class)
					.addMember("type", "$T.class", StringObject.class).build();

			final AnnotationSpec.Builder filterSpec = AnnotationSpec.builder(TypeFilter.class)
					.addMember("type", "$L", constraintSpec);

			final AnnotationSpec converterSpec = AnnotationSpec.builder(DeclaredConverter.class)
					.addMember("value", "$L", filterSpec.build()).build();

			final String converterName = generateClassName();
			JavaSource converter = new JavaSource(TEST_PACKAGE, converterName, Modifier.PUBLIC)
					.builderTransformer(
							(builder, source) -> builder.superclass(StringObjectTypeConverter.class)
									.addAnnotation(converterSpec));
			sources.add(converter);

		} else {
			fieldBuilder.addAnnotation(AnnotationSpec.builder(Retained.class)
					.addMember("converter", "$T.class", StringObjectTypeConverter.class).build());
		}
		final JavaSource testClass = new JavaSource(TEST_PACKAGE, generateClassName(),
				Modifier.PUBLIC).fields(fieldBuilder.build());
		// out test class always goes in front
		sources.add(0, testClass);
		final TestEnvironment environment = new TestEnvironment(this, sources);
		environment.invokeSaveAndRestore();
	}

	public static class StringObjectTypeConverter implements TypeConverter<StringObject> {

		@Override
		public void save(Bundle bundle, StringObject stringObject, String key) {
			bundle.putString(key, stringObject.actualString);
		}

		@Override
		public StringObject restore(Bundle bundle, StringObject stringObject, String key) {
			stringObject.actualString = bundle.getString(key);
			return stringObject;
		}
	}

	@Target(ElementType.TYPE)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface RandomAnnotation {

	}

	public static abstract class BaseStringObject {

	}

	@RandomAnnotation
	public static class StringObject extends BaseStringObject {
		private String actualString;

		public StringObject(String actualString) {
			this.actualString = actualString;
		}

		public static String wrap(StringObject object) {
			return object == null ? null : object.actualString;
		}

		public static StringObject unwrap(String actualString) {
			return new StringObject(actualString);
		}
	}

	public static class InheritedStringObject extends StringObject {

		public InheritedStringObject(String actualString) {
			super(actualString);
		}
	}

}
