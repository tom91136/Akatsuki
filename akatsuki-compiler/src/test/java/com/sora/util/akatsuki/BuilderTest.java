package com.sora.util.akatsuki;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import org.junit.Test;

import android.app.Activity;
import android.app.Service;
import android.support.v4.app.Fragment;

import com.sora.util.akatsuki.ArgConfig.BuilderType;
import com.sora.util.akatsuki.BuilderTestEnvironment.SingleBuilderTester;
import com.sora.util.akatsuki.RetainedStateTestEnvironment.Accessor;
import com.sora.util.akatsuki.RetainedStateTestEnvironment.BundleRetainerTester;
import com.squareup.javapoet.AnnotationSpec;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BuilderTest extends BuilderTestBase {

	@Test
	public void testSimpleBuilder() {
		testSingleClass(TEST_PACKAGE_NAME, Fragment.class, testField());
	}

	@Test
	public void testAllSupportedBuilderValid() {
		Class<?>[] classes = { Fragment.class, android.app.Fragment.class, Activity.class,
				Service.class };
		for (Class<?> clazz : classes) {
			testSingleClass(TEST_PACKAGE_NAME, clazz, testField());
		}
	}

	@Test(expected = RuntimeException.class)
	public void testUnsupportedBuilder() {
		// should not compile at all
		testSingleClass(TEST_PACKAGE_NAME, Object.class, testField());
	}

	@Test
	public void testBuilderClassExists() throws ClassNotFoundException {
		BuilderTestEnvironment environment = testSingleClass(TEST_PACKAGE_NAME, Fragment.class,
				testField());
		Class<?> builderClass = environment
				.findClass(TEST_PACKAGE_NAME + "." + ArgumentBuilderModel.BUILDER_CLASS_NAME);
		assertNotNull(builderClass);
	}

	@Test
	public void testBuilderClassHasCorrectStructure() throws ClassNotFoundException {
		for (String name : TEST_PACKAGE_NAMES) {
			BuilderTestEnvironment environment = testSingleClass(name, Fragment.class, testField());
			environment.assertAllBuildersGeneratedAndValid();
		}
	}

	@Test
	public void testBuilderClassHasCorrectStructureTogether() throws ClassNotFoundException {
		List<JavaSource> sources = Arrays.stream(TEST_PACKAGE_NAMES)
				.map(name -> createTestSource(name, Fragment.class, testField()))
				.collect(Collectors.toList());
		BuilderTestEnvironment environment = new BuilderTestEnvironment(this, sources);
		environment.assertAllBuildersGeneratedAndValid();
	}

	@Test(expected = AssertionError.class)
	public void testBuilderRetainerSaveShouldFail() throws Exception {
		testSingleBuilder().retainerTester().invokeSave();
	}

	@Test
	public void testBuilderRetainerRestore() {
		ArgField[] fields = Arrays.stream(SupportedTypeTest.SUPPORTED_SIMPLE_CLASSES)
				.map(ArgField::new).toArray(ArgField[]::new);
		BuilderTestEnvironment environment = testSingleClass("test", Fragment.class, fields);
		List<SingleBuilderTester> testers = environment.assertAllBuildersGeneratedAndValid();
		BundleRetainerTester tester = testers.get(0).retainerTester();
		tester.invokeRestore();
		tester.executeTestCaseWithFields(new HashSet<>(Arrays.asList(fields)), n -> true,
				BundleRetainerTester.CLASS, Accessor.GET);
	}

	@Test
	public void testBuilderSimpleInheritance() {
		JavaSource parent = createTestSource(TEST_PACKAGE_NAME, Fragment.class, testField());
		JavaSource child = createTestSource(TEST_PACKAGE_NAME, null,
				new ArgField(String.class, "anotherField")).superClass(parent);
		BuilderTestEnvironment environment = new BuilderTestEnvironment(this, parent, child);
		environment.assertAllBuildersGeneratedAndValid();
	}

	@Test
	public void testBuilderInheritanceWithGap() {
		JavaSource parent = createTestSource(TEST_PACKAGE_NAME, Fragment.class, testField());
		JavaSource gap = createTestSource(TEST_PACKAGE_NAME, null,
				new Field(String.class, "anotherField1")).superClass(parent);
		JavaSource child = createTestSource(TEST_PACKAGE_NAME, null,
				new ArgField(String.class, "anotherField2")).superClass(gap);
		BuilderTestEnvironment environment = new BuilderTestEnvironment(this, parent, gap, child);
		for (JavaSource source : Arrays.asList(parent, child)) {
			new SingleBuilderTester(environment, source).initializeAndValidate();
		}
	}

	@Test
	public void testBuilderDeepInheritance() {
		ArgField[] fields = createSimpleArgFields();
		JavaSource[] sources = new JavaSource[fields.length];
		JavaSource lastSource = createTestSource(TEST_PACKAGE_NAME, Fragment.class, fields[0]);
		sources[0] = lastSource;
		for (int i = 1; i < fields.length; i++) {
			sources[i] = createTestSource(TEST_PACKAGE_NAME, null, fields[i])
					.superClass(lastSource);
			lastSource = sources[i];
		}
		BuilderTestEnvironment environment = new BuilderTestEnvironment(this,
				Arrays.asList(sources));

		environment.assertAllBuildersGeneratedAndValid();
	}

	@Test
	public void testBuilderCreatedForAllChildren() {
		JavaSource parent = createTestSource(TEST_PACKAGE_NAME, Fragment.class, testField());
		JavaSource[] children = Arrays.stream(TEST_PACKAGE_NAMES)
				.map(n -> createTestSource(n, null).superClass(parent)).toArray(JavaSource[]::new);
		BuilderTestEnvironment environment = new BuilderTestEnvironment(this, parent, children);
		environment.assertAllBuildersGeneratedAndValid();
	}

	@Test
	public void testCheckedBuilderWithNoFieldOptional() {
		for (BuilderType type : CHECKED_TYPES) {
			ArgField[] fields = createSimpleArgFields();
			SingleBuilderTester tester = testFields(type, fields);
			tester.builderInstance.check();
			for (ArgField field : fields) {
				verify(tester.builderInstance.bundle).containsKey(field.name);
			}
			verifyNoMoreInteractions(tester.builderInstance.bundle);
		}
	}

	@Test
	public void testCheckedBuilderWithAllFieldOptional() {
		for (BuilderType type : CHECKED_TYPES) {
			SingleBuilderTester tester = testFields(type, createArgFields(
					SupportedTypeTest.SUPPORTED_SIMPLE_CLASSES, af -> af.optional(true)));
			tester.builderInstance.check();
			verifyZeroInteractions(tester.builderInstance.bundle);
		}
	}

	@Test
	public void testCheckedBuilderWithSomeFieldOptional() {
		for (BuilderType type : CHECKED_TYPES) {
			ArgField[] fields = createArgFields(SupportedTypeTest.SUPPORTED_SIMPLE_CLASSES,
					Function.identity());
			ArgField[] optionalFields = createArgFields(SupportedTypeTest.SUPPORTED_ARRAY_CLASSES,
					af -> af.optional(true));
			SingleBuilderTester tester = testFields(type,
					concatArray(ArgField.class, fields, optionalFields));
			tester.builderInstance.check();
			for (ArgField field : fields) {
				verify(tester.builderInstance.bundle).containsKey(field.name);
			}
			verifyNoMoreInteractions(tester.builderInstance.bundle);
		}
	}

	@Test
	public void testUncheckedBuilderShouldNotCheck() {
		for (BuilderType type : UNCHECKED_TYPES) {
			ArgField[] fields = Stream.concat(
					createArgFieldStream(SupportedTypeTest.SUPPORTED_SIMPLE_CLASSES,
							Function.identity()),
					createArgFieldStream(SupportedTypeTest.SUPPORTED_ARRAY_CLASSES,
							af -> af.optional(true)))
					.toArray(ArgField[]::new);
			SingleBuilderTester tester = testFields(type, fields);
			tester.builderInstance.check();
			verifyZeroInteractions(tester.builderInstance.bundle);
		}
	}

	@Test
	public void testSkipAll() {
		for (BuilderType type : BuilderType.values()) {
			ArgField[] fields = createArgFields(SupportedTypeTest.SUPPORTED_SIMPLE_CLASSES,
					af -> af.skip(true));
			SingleBuilderTester tester = testFields(type, fields);

			for (ArgField field : fields) {
				tester.filterAndCountMethod(0,
						m -> methodParameterMatch(m, field.clazz) && isMethodPublic(m), methods -> {
							throw new AssertionError("Field " + field + " is skipped but the"
									+ " builder  still has the accessor for it, found: " + methods);
						});
			}
		}
	}

	@Test
	public void testSkipSome() {
		for (BuilderType type : BuilderType.values()) {
			ArgField[] fields = createArgFields(SupportedTypeTest.SUPPORTED_SIMPLE_CLASSES,
					Function.identity());
			ArgField[] skippedFields = createArgFields(SupportedTypeTest.SUPPORTED_ARRAY_CLASSES,
					af -> af.skip(true));
			SingleBuilderTester tester = testFields(type,
					concatArray(ArgField.class, fields, skippedFields));
			for (ArgField field : fields) {
				tester.assertMethodCountMatched(1,
						m -> methodParameterMatch(m, field.clazz) && isMethodPublic(m));
			}
			for (ArgField field : skippedFields) {
				tester.filterAndCountMethod(0,
						m -> methodParameterMatch(m, field.clazz) && isMethodPublic(m), methods -> {
							throw new AssertionError("Field " + field + " is skipped but the"
									+ " builder  still has the accessor for it, found: " + methods);
						});
			}

		}
	}

	@Test
	public void testChainedBuilderHasCorrectSignature() {
		for (BuilderType type : Arrays.asList(BuilderType.CHAINED_CHECKED,
				BuilderType.CHAINED_UNCHECKED)) {
			ArgField field = testField();
			BuilderTestEnvironment environment = testSingleClass(argConfigForType(type), "test",
					Fragment.class, field);
			SingleBuilderTester tester = environment.assertAllBuildersGeneratedAndValid().get(0);
			tester.assertMethodCountMatched(1,
					m -> m.getReturnType() == tester.builderInstance.getClass()
							&& methodParameterMatch(m, field.clazz) && isMethodPublic(m));
		}
	}

	@Test
	public void testVoidBuilderHasCorrectSignature() {
		for (BuilderType type : Arrays.asList(BuilderType.CHECKED, BuilderType.UNCHECKED)) {
			ArgField field = testField();
			BuilderTestEnvironment environment = testSingleClass(argConfigForType(type), "test",
					Fragment.class, field);
			SingleBuilderTester tester = environment.assertAllBuildersGeneratedAndValid().get(0);
			tester.assertMethodCountMatched(1, m -> m.getReturnType() == void.class
					&& methodParameterMatch(m, field.clazz) && isMethodPublic(m));
		}
	}

}
