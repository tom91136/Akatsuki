package com.sora.util.akatsuki;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.lang.model.element.Modifier;

import org.junit.Test;

import android.app.Activity;
import android.app.Service;
import android.support.v4.app.Fragment;

import com.sora.util.akatsuki.ArgConfig.BuilderType;
import com.sora.util.akatsuki.BuilderIntegrationTest.A.A$B;
import com.sora.util.akatsuki.BuilderTestEnvironment.SingleBuilderTester;
import com.sora.util.akatsuki.RetainedStateTestEnvironment.Accessor;
import com.sora.util.akatsuki.RetainedStateTestEnvironment.BundleRetainerTester;

public class BuilderIntegrationTest extends BuilderIntegrationTestBase {

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
				.findClass(TEST_PACKAGE_NAME + "." + Internal.BUILDER_CLASS_NAME);
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
		List<TestSource> sources = Arrays.stream(TEST_PACKAGE_NAMES)
				.map(name -> createTestSource(name, Fragment.class, testField()))
				.collect(Collectors.toList());
		BuilderTestEnvironment environment = new BuilderTestEnvironment(this, sources);
		environment.assertAllBuildersGeneratedAndValid();
	}

	public static class A {
		public static class A$B {

		}
	}

	@Test
	public void testStaticInnerClassHasCorrectStructure() throws ClassNotFoundException {
		TestSource topLevel = createTestSource(TEST_PACKAGE_NAME, Fragment.class, testField());
		TestSource last = topLevel;
		for (int i = 0; i < 5; i++) {
			TestSource inner = createTestSource(null, Fragment.class, testField());
			last.innerClasses(true, inner);
			last = inner;
		}
		BuilderTestEnvironment environment = new BuilderTestEnvironment(this, topLevel);
		environment.assertAllBuildersGeneratedAndValid();

		Class.forName(A$B.class.getName());
	}

	@Test(expected = AssertionError.class)
	public void testBuilderRetainerSaveShouldFail() throws Exception {
		testSingleBuilder().retainerTester().invokeSave();
	}

	@Test
	public void testBuilderRetainerRestore() {
		ArgTestField[] fields = Arrays.stream(SupportedTypeIntegrationTest.SUPPORTED_SIMPLE_CLASSES)
				.map(ArgTestField::new).toArray(ArgTestField[]::new);
		BuilderTestEnvironment environment = testSingleClass("test", Fragment.class, fields);
		List<SingleBuilderTester> testers = environment.assertAllBuildersGeneratedAndValid();
		BundleRetainerTester tester = testers.get(0).retainerTester();
		tester.invokeRestore();
		tester.executeTestCaseWithFields(new HashSet<>(Arrays.asList(fields)), n -> true,
				BundleRetainerTester.CLASS_EQ, Accessor.GET, f -> 1);
	}

	@Test
	public void testBuilderSimpleInheritance() {
		TestSource parent = createTestSource(TEST_PACKAGE_NAME, Fragment.class, testField());
		TestSource child = createTestSource(TEST_PACKAGE_NAME, null,
				new ArgTestField(String.class, "anotherField")).superClass(parent);
		BuilderTestEnvironment environment = new BuilderTestEnvironment(this, parent, child);
		environment.assertAllBuildersGeneratedAndValid();
	}

	@Test
	public void testBuilderInheritanceWithGap() {
		TestSource parent = createTestSource(TEST_PACKAGE_NAME, Fragment.class, testField());

		TestSource gap = createTestSource(TEST_PACKAGE_NAME, null, new TestField(String.class, "a"))
				.superClass(parent);

		TestSource child = createTestSource(TEST_PACKAGE_NAME, null,
				new ArgTestField(String.class, "b")).superClass(gap);

		BuilderTestEnvironment environment = new BuilderTestEnvironment(this, parent, gap, child);
		for (TestSource source : Arrays.asList(parent, child)) {
			new SingleBuilderTester(environment, source).initializeAndValidate();
		}
	}

	@Test
	public void testBuilderInheritanceWithMultipleGap() {
		TestSource parent = createTestSource(TEST_PACKAGE_NAME, Fragment.class, testField());

		TestSource gap = createTestSource(TEST_PACKAGE_NAME, null, new TestField(String.class, "a"))
				.superClass(parent);

		TestSource gap2 = createTestSource(TEST_PACKAGE_NAME, null,
				new TestField(String.class, "b")).superClass(gap);

		TestSource gap3 = createTestSource(TEST_PACKAGE_NAME, null,
				new TestField(String.class, "c")).superClass(gap2);

		TestSource child = createTestSource(TEST_PACKAGE_NAME, null,
				new ArgTestField(String.class, "d")).superClass(gap3);

		BuilderTestEnvironment environment = new BuilderTestEnvironment(this, parent, gap, gap2,
				gap3, child);
		for (TestSource source : Arrays.asList(parent, child)) {
			new SingleBuilderTester(environment, source).initializeAndValidate();
		}
	}

	@Test
	public void testBuilderDeepInheritance() {
		ArgTestField[] fields = createSimpleArgFields();
		TestSource[] sources = new TestSource[fields.length];
		TestSource lastSource = createTestSource(TEST_PACKAGE_NAME, Fragment.class, fields[0]);
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
	public void testBuilderCreatedForInnerChildrenDifferentPackage() {
		TestSource parent = createTestSource(TEST_PACKAGE_NAME, Fragment.class, testField());
		TestSource source = createTestSource(null, null).superClass(parent);
		TestSource enclosingClass = new TestSource(TEST_PACKAGE_NAMES[0], generateClassName(),
				Modifier.PUBLIC).innerClasses(true,source);
		BuilderTestEnvironment environment = new BuilderTestEnvironment(this, parent,
				enclosingClass);
		new SingleBuilderTester(environment, source);
	}

	@Test
	public void testBuilderCreatedForAllInnerChildrenDifferentPackage() {
		TestSource parent = createTestSource(TEST_PACKAGE_NAME, Fragment.class, testField());

		Map<String, TestSource> childTestClassMap = Arrays.stream(TEST_PACKAGE_NAMES)
				.collect(Collectors.toMap(Function.identity(),
						n -> createTestSource(null, null).superClass(parent)));

		TestSource[] enclosingClasses = childTestClassMap.entrySet().stream()
				.map(entry -> new TestSource(entry.getKey(), generateClassName(), Modifier.PUBLIC)
						.innerClasses(true,entry.getValue()))
				.toArray(TestSource[]::new);

		BuilderTestEnvironment environment = new BuilderTestEnvironment(this, parent,
				enclosingClasses);

		childTestClassMap.values()
				.forEach(ts -> new SingleBuilderTester(environment, ts).initializeAndValidate());
	}

	@Test
	public void testBuilderCreatedForAllChildrenDifferentPackage() {
		TestSource parent = createTestSource(TEST_PACKAGE_NAME, Fragment.class, testField());
		TestSource[] children = Arrays.stream(TEST_PACKAGE_NAMES)
				.map(n -> createTestSource(n, null).superClass(parent)).toArray(TestSource[]::new);
		BuilderTestEnvironment environment = new BuilderTestEnvironment(this, parent, children);
		environment.assertAllBuildersGeneratedAndValid();
	}

	@Test
	public void testBuilderCreatedForSingleChildrenSamePackage() {
		TestSource parent = createTestSource(TEST_PACKAGE_NAME, Fragment.class, testField());
		BuilderTestEnvironment environment = new BuilderTestEnvironment(this, parent,
				createTestSource(TEST_PACKAGE_NAME, null).superClass(parent));
		environment.assertAllBuildersGeneratedAndValid();
	}

	@Test
	public void testBuilderCreatedForSingleChildrenDifferentPackage() {
		TestSource parent = createTestSource(TEST_PACKAGE_NAME, Fragment.class, testField());
		BuilderTestEnvironment environment = new BuilderTestEnvironment(this, parent,
				createTestSource(TEST_PACKAGE_NAMES[0], null).superClass(parent));
		environment.assertAllBuildersGeneratedAndValid();
	}

	@Test
	public void testCheckedBuilderWithNoFieldOptional() {
		for (BuilderType type : CHECKED_TYPES) {
			ArgTestField[] fields = createSimpleArgFields();
			SingleBuilderTester tester = testFields(type, fields);
			tester.builderInstance.check();
			for (ArgTestField field : fields) {
				verify(tester.builderInstance.bundle).containsKey(field.name);
			}
			verifyNoMoreInteractions(tester.builderInstance.bundle);
		}
	}

	@Test
	public void testCheckedBuilderWithAllFieldOptional() {
		for (BuilderType type : CHECKED_TYPES) {
			SingleBuilderTester tester = testFields(type,
					createArgFields(SupportedTypeIntegrationTest.SUPPORTED_SIMPLE_CLASSES,
							af -> af.optional(true)));
			tester.builderInstance.check();
			verifyZeroInteractions(tester.builderInstance.bundle);
		}
	}

	@Test
	public void testCheckedBuilderWithSomeFieldOptional() {
		for (BuilderType type : CHECKED_TYPES) {
			ArgTestField[] fields = createArgFields(
					SupportedTypeIntegrationTest.SUPPORTED_SIMPLE_CLASSES, Function.identity());
			ArgTestField[] optionalFields = createArgFields(
					SupportedTypeIntegrationTest.SUPPORTED_ARRAY_CLASSES, af -> af.optional(true));
			SingleBuilderTester tester = testFields(type,
					concatArray(ArgTestField.class, fields, optionalFields));
			tester.builderInstance.check();
			for (ArgTestField field : fields) {
				verify(tester.builderInstance.bundle).containsKey(field.name);
			}
			verifyNoMoreInteractions(tester.builderInstance.bundle);
		}
	}

	@Test
	public void testUncheckedBuilderShouldNotCheck() {
		for (BuilderType type : UNCHECKED_TYPES) {
			ArgTestField[] fields = Stream.concat(
					createArgFieldStream(SupportedTypeIntegrationTest.SUPPORTED_SIMPLE_CLASSES,
							Function.identity()),
					createArgFieldStream(SupportedTypeIntegrationTest.SUPPORTED_ARRAY_CLASSES,
							af -> af.optional(true)))
					.toArray(ArgTestField[]::new);
			SingleBuilderTester tester = testFields(type, fields);
			tester.builderInstance.check();
			verifyZeroInteractions(tester.builderInstance.bundle);
		}
	}

	@Test
	public void testSkipAll() {
		for (BuilderType type : BuilderType.values()) {
			ArgTestField[] fields = createArgFields(
					SupportedTypeIntegrationTest.SUPPORTED_SIMPLE_CLASSES, af -> af.skip(true));
			SingleBuilderTester tester = testFields(type, fields);

			for (ArgTestField field : fields) {
				tester.filterAndCountMethod(0,
						m -> methodParameterMatch(m, field.clazz) && isMethodPublic(m), methods -> {
							throw new AssertionError("TestField " + field + " is skipped but the"
									+ " builder  still has the accessor for it, found: " + methods);
						});
			}
		}
	}

	@Test
	public void testSkipSome() {
		for (BuilderType type : BuilderType.values()) {
			ArgTestField[] fields = createArgFields(
					SupportedTypeIntegrationTest.SUPPORTED_SIMPLE_CLASSES, Function.identity());
			ArgTestField[] skippedFields = createArgFields(
					SupportedTypeIntegrationTest.SUPPORTED_ARRAY_CLASSES, af -> af.skip(true));
			SingleBuilderTester tester = testFields(type,
					concatArray(ArgTestField.class, fields, skippedFields));
			for (ArgTestField field : fields) {
				tester.assertMethodCountMatched(1,
						m -> methodParameterMatch(m, field.clazz) && isMethodPublic(m));
			}
			for (ArgTestField field : skippedFields) {
				tester.filterAndCountMethod(0,
						m -> methodParameterMatch(m, field.clazz) && isMethodPublic(m), methods -> {
							throw new AssertionError("TestField " + field + " is skipped but the"
									+ " builder  still has the accessor for it, found: " + methods);
						});
			}

		}
	}

	@Test
	public void testChainedBuilderHasCorrectSignature() {
		for (BuilderType type : Arrays.asList(BuilderType.CHAINED_CHECKED,
				BuilderType.CHAINED_UNCHECKED)) {
			ArgTestField field = testField();
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
			ArgTestField field = testField();
			BuilderTestEnvironment environment = testSingleClass(argConfigForType(type), "test",
					Fragment.class, field);
			SingleBuilderTester tester = environment.assertAllBuildersGeneratedAndValid().get(0);
			tester.assertMethodCountMatched(1, m -> m.getReturnType() == void.class
					&& methodParameterMatch(m, field.clazz) && isMethodPublic(m));
		}
	}

}
