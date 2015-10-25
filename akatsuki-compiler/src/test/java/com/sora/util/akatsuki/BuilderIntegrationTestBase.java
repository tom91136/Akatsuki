package com.sora.util.akatsuki;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.lang.model.element.Modifier;

import android.support.v4.app.Fragment;

import com.google.common.collect.Sets;
import com.sora.util.akatsuki.ArgConfig.BuilderType;
import com.sora.util.akatsuki.BuilderTestEnvironment.SingleBuilderTester;
import com.squareup.javapoet.AnnotationSpec;

public abstract class BuilderIntegrationTestBase extends IntegrationTestBase {

	protected static final String TEST_PACKAGE_NAME = "testArg";
	public static final String TEST_FIELD_NAME = "a";
	public static final String[] TEST_PACKAGE_NAMES = new String[] { "a", "A", "b", "a.b", "a.b.c",
			"test.a", };

	public static final BuilderType[] CHECKED_TYPES = { BuilderType.CHECKED,
			BuilderType.CHAINED_CHECKED };
	public static final BuilderType[] UNCHECKED_TYPES = { BuilderType.UNCHECKED,
			BuilderType.CHAINED_UNCHECKED };

	static boolean isMethodPublic(Method method) {
		return java.lang.reflect.Modifier.isPublic(method.getModifiers());
	}

	static boolean methodParameterMatch(Method method, Class<?>... parameterClasses) {
		return Arrays.equals(method.getParameterTypes(), parameterClasses);
	}

	protected TestSource createTestSource(AnnotationSpec spec, String packageName,
			Class<?> parentClass, TestField... fields) {
		final HashSet<TestField> set = Sets.newHashSet(fields);
		if (set.size() != fields.length)
			throw new IllegalArgumentException("Duplicate fields are not allowed");

		// mockito explodes if the classes are not public...
		// we use abstract just in case of our superclass is abstract too
		final TestSource source = new TestSource(packageName, generateClassName(), Modifier.PUBLIC,
				Modifier.ABSTRACT).appendFields(set.stream().map(f -> {
			if (!(f instanceof ArgTestField)) {
				f.fieldSpecBuilder().addAnnotation(Arg.class);
			}
			return f.createFieldSpec();
		}).collect(Collectors.toList()));

		if (spec != null)
			source.appendTransformation((b, s) -> b.addAnnotation(spec));
		if (parentClass != null)
			source.appendTransformation((builder, s) -> builder.superclass(parentClass));

		return source;
	}

	protected TestSource createTestSource(String packageName, Class<?> parentClass,
			TestField... fields) {
		return createTestSource(null, packageName, parentClass, fields);
	}

	protected BuilderTestEnvironment testSingleClass(String packageName, Class<?> parentClass,
			TestField... fields) {
		return testSingleClass(null, packageName, parentClass, fields);
	}

	protected BuilderTestEnvironment testSingleClass(AnnotationSpec spec, String packageName,
			Class<?> parentClass, TestField... fields) {
		return new BuilderTestEnvironment(this,
				createTestSource(spec, packageName, parentClass, fields));
	}

	protected SingleBuilderTester testSingleBuilder() {
		BuilderTestEnvironment environment = testSingleClass("test", Fragment.class, testField());
		return environment.assertAllBuildersGeneratedAndValid().get(0);
	}

	protected ArgTestField testField() {
		return new ArgTestField(String.class, TEST_FIELD_NAME);
	}

	public SingleBuilderTester testFields(BuilderType type, ArgTestField... fields) {
		AnnotationSpec spec = AnnotationSpec.builder(ArgConfig.class)
				.addMember("type", "$T.$L", BuilderType.class, type).build();
		BuilderTestEnvironment environment = testSingleClass(spec, "test", Fragment.class, fields);
		return environment.assertAllBuildersGeneratedAndValid().get(0);
	}

	public ArgTestField[] createSimpleArgFields() {
		return Arrays.stream(SupportedTypeIntegrationTest.SUPPORTED_SIMPLE_CLASSES).map(ArgTestField::new)
				.toArray(ArgTestField[]::new);
	}

	public ArgTestField[] createArgFields(Class<?>[] classes,
			Function<ArgTestField, ArgTestField> transformation) {
		return Arrays.stream(classes).map(ArgTestField::new).map(transformation)
				.toArray(ArgTestField[]::new);
	}

	public Stream<ArgTestField> createArgFieldStream(Class<?>[] classes,
			Function<ArgTestField, ArgTestField> transformation) {
		return Arrays.stream(classes).map(ArgTestField::new).map(transformation);
	}

	public AnnotationSpec argConfigForType(BuilderType type) {
		return AnnotationSpec.builder(ArgConfig.class)
				.addMember("type", "$T.$L", BuilderType.class, type).build();
	}

	@SuppressWarnings("unchecked")
	public <T> T[] concatArray(Class<T> clazz, T[] lhs, T[] rhs) {
		return Stream.concat(Arrays.stream(lhs), Arrays.stream(rhs))
				.toArray(n -> (T[]) Array.newInstance(clazz, n));
	}
}
