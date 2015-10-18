package com.sora.util.akatsuki;

import android.support.v4.app.Fragment;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.lang.model.element.Modifier;

import com.google.common.collect.Sets;
import com.sora.util.akatsuki.ArgConfig.BuilderType;
import com.sora.util.akatsuki.BuilderTestEnvironment.SingleBuilderTester;
import com.squareup.javapoet.AnnotationSpec;

public abstract class BuilderTestBase extends TestBase {

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

	protected JavaSource createTestSource(AnnotationSpec spec, String packageName,
			Class<?> parentClass, Field... fields) {
		final HashSet<Field> set = Sets.newHashSet(fields);
		if (set.size() != fields.length)
			throw new IllegalArgumentException("Duplicate fields are not allowed");

		// mockito explodes if the classes are not public...
		// we use abstract just in case of our superclass is abstract too
		final JavaSource source = new JavaSource(packageName, generateClassName(), Modifier.PUBLIC,
				Modifier.ABSTRACT).fields(set.stream().map(f -> {
					if (!(f instanceof ArgField)) {
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

	protected JavaSource createTestSource(String packageName, Class<?> parentClass,
			Field... fields) {
		return createTestSource(null, packageName, parentClass, fields);
	}

	protected BuilderTestEnvironment testSingleClass(String packageName, Class<?> parentClass,
			Field... fields) {
		return testSingleClass(null, packageName, parentClass, fields);
	}

	protected BuilderTestEnvironment testSingleClass(AnnotationSpec spec, String packageName,
			Class<?> parentClass, Field... fields) {
		return new BuilderTestEnvironment(this,
				createTestSource(spec, packageName, parentClass, fields));
	}

	protected SingleBuilderTester testSingleBuilder() {
		BuilderTestEnvironment environment = testSingleClass("test", Fragment.class, testField());
		return environment.assertAllBuildersGeneratedAndValid().get(0);
	}

	protected ArgField testField() {
		return new ArgField(String.class, TEST_FIELD_NAME);
	}

	public SingleBuilderTester testFields(BuilderType type, ArgField... fields) {
		AnnotationSpec spec = AnnotationSpec.builder(ArgConfig.class)
				.addMember("type", "$T.$L", BuilderType.class, type).build();
		BuilderTestEnvironment environment = testSingleClass(spec, "test", Fragment.class, fields);
		return environment.assertAllBuildersGeneratedAndValid().get(0);
	}

	public ArgField[] createSimpleArgFields() {
		return Arrays.stream(SupportedTypeTest.SUPPORTED_SIMPLE_CLASSES).map(ArgField::new)
				.toArray(ArgField[]::new);
	}

	public ArgField[] createArgFields(Class<?>[] classes,
			Function<ArgField, ArgField> transformation) {
		return Arrays.stream(classes).map(ArgField::new).map(transformation)
				.toArray(ArgField[]::new);
	}

	public Stream<ArgField> createArgFieldStream(Class<?>[] classes,
			Function<ArgField, ArgField> transformation) {
		return Arrays.stream(classes).map(ArgField::new).map(transformation);
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
