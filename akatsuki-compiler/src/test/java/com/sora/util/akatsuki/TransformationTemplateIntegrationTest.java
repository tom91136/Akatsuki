package com.sora.util.akatsuki;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collections;

import javax.lang.model.element.Modifier;

import org.junit.Test;

import android.os.Bundle;

import com.sora.util.akatsuki.RetainedStateTestEnvironment.BundleRetainerTester;
import com.sora.util.akatsuki.TransformationTemplate.Execution;
import com.sora.util.akatsuki.TransformationTemplate.StatementTemplate;
import com.sora.util.akatsuki.TypeConstraint.Bound;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.FieldSpec.Builder;

public class TransformationTemplateIntegrationTest extends RetainedStateIntegrationTestBase {

	@Test
	public void testClassConstraint() {
		testTransformationTemplate(Bound.EXACTLY, StringObject.class, StringObject.class);
	}

	@Test(expected = RuntimeException.class)
	public void testClassConstraintOnInterfaces() {
		// you can't retain an interface, it has no fields
		testTransformationTemplate(Bound.EXACTLY, StringObject.class, RandomInterface.class);
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
	public void testSubClassConstraintOnInterfaces() {
		testTransformationTemplate(Bound.EXTENDS, StringObject.class, RandomInterface.class);
	}

	@Test
	public void testSuperClassConstraint() {
		testTransformationTemplate(Bound.SUPER, StringObject.class, BaseStringObject.class);
	}

	@Test
	public void testSuperClassConstraintOnInterfaces() {
		testTransformationTemplate(Bound.SUPER, StringObject.class, BaseRandomInterface.class);
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
	// @Test
	// public void testCompoundType() {
	// final TestSource testClass = new TestSource(TEST_PACKAGE,
	// generateClassName(),
	// Modifier.PUBLIC).fields(new RetainedTestField(List.class, "a",
	// "new ArrayList<>()", StringObject.class)
	// .createFieldSpec());
	// testClass.builderTransformer(
	// (builder, source) -> builder.addAnnotation(createTransformationTemplate(
	// Bound.EXACTLY, StringObject.class, RandomAnnotation.class)));
	//
	// final TestEnvironment environment = new TestEnvironment(this, testClass);
	//
	// environment.invokeSaveAndRestore();
	// environment.testSaveRestoreInvocation(n -> true,
	// TestEnvironment.CLASS_EQ,
	// Collections.singleton(new RetainedTestField(List.class, "a",
	// StringObject.class)));
	//
	// }

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

		final TestSource testClass = new TestSource(TEST_PACKAGE, generateClassName(),
				Modifier.PUBLIC)
						.appendFields(new RetainedTestField(StringObject.class, "a", "new " +
								                                                             objectFqcn + "(\"A\")").createFieldSpec());
		testClass.appendTransformation((builder, source) -> builder
				.addAnnotation(createTransformationTemplate(bound, staticClass, constraints)));

		final RetainedStateTestEnvironment environment = new RetainedStateTestEnvironment(this,
				testClass);

		environment.tester().invokeSaveAndRestore();
		environment.tester().testSaveRestoreInvocation(n -> true, BundleRetainerTester.CLASS_EQ,
				Collections.singleton(new RetainedTestField(String.class, "a")), f -> 1);
	}

	protected void testTypeConverter(boolean registered) {
		final TestField retainedField = new TestField(StringObject.class, "a",
				"new " + StringObject.class.getCanonicalName() + "(\"A\")");
		final Builder fieldBuilder = retainedField.fieldSpecBuilder();
		final ArrayList<TestSource> sources = new ArrayList<>();
		if (registered) {
			fieldBuilder.addAnnotation(Retained.class);
			final AnnotationSpec constraintSpec = AnnotationSpec.builder(TypeConstraint.class)
					.addMember("type", "$T.class", StringObject.class).build();

			final AnnotationSpec.Builder filterSpec = AnnotationSpec.builder(TypeFilter.class)
					.addMember("type", "$L", constraintSpec);

			final AnnotationSpec converterSpec = AnnotationSpec.builder(DeclaredConverter.class)
					.addMember("value", "$L", filterSpec.build()).build();

			final String converterName = generateClassName();
			TestSource converter = new TestSource(TEST_PACKAGE, converterName, Modifier.PUBLIC)
					.appendTransformation(
							(builder, source) -> builder.superclass(StringObjectTypeConverter.class)
									.addAnnotation(converterSpec));
			sources.add(converter);

		} else {
			fieldBuilder.addAnnotation(AnnotationSpec.builder(Retained.class).build());
			fieldBuilder.addAnnotation(AnnotationSpec.builder(With.class)
					.addMember("value", "$T.class", StringObjectTypeConverter.class).build());
		}
		final TestSource testClass = new TestSource(TEST_PACKAGE, generateClassName(),
				Modifier.PUBLIC).appendFields(fieldBuilder.build());
		// out test class always goes in front
		sources.add(0, testClass);
		final RetainedStateTestEnvironment environment = new RetainedStateTestEnvironment(this,
				sources);
		environment.tester().invokeSaveAndRestore();
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

	public interface BaseRandomInterface {

	}

	public interface RandomInterface extends BaseRandomInterface {

	}

	@RandomAnnotation
	public static class StringObject extends BaseStringObject implements RandomInterface {
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
