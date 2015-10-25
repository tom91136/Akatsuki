package com.sora.util.akatsuki;

import javax.lang.model.element.Modifier;

import org.junit.experimental.runners.Enclosed;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import com.squareup.javapoet.FieldSpec;

@RunWith(Enclosed.class)
public class EnclosingInstanceValidationIntegrationTest extends IntegrationTestBase {

	public static final FieldSpec FOO = field(STRING_TYPE, "foo", Retained.class);
	public static final FieldSpec BAR = field(STRING_TYPE, "bar", Retained.class);

	@RunWith(Theories.class)
	public static class EnclosingInstanceValidIntegrationTest extends IntegrationTestBase {

		@DataPoint public static final TestSource STATIC_NESTED_CLASS = new TestSource(TEST_PACKAGE,
				TEST_CLASS, Modifier.PUBLIC).appendFields(FOO).innerClasses(
						new TestSource(TEST_CLASS + "Inner1", Modifier.PUBLIC, Modifier.STATIC)
								.appendFields(BAR));

		@DataPoint public static final TestSource STATIC_MULTIPLE_NESTED_CLASS = new TestSource(
				TEST_PACKAGE, TEST_CLASS, Modifier.PUBLIC).appendFields(FOO).innerClasses(
						new TestSource(TEST_CLASS + "Inner1", Modifier.PUBLIC, Modifier.STATIC)
								.appendFields(BAR),
						new TestSource(TEST_CLASS + "Inner2", Modifier.PUBLIC, Modifier.STATIC)
								.appendFields(BAR));

		@DataPoint public static final TestSource PACKAGE_PRIVATE_CLASS = new TestSource(
				TEST_PACKAGE, TEST_CLASS).appendFields(FOO);

		@Theory
		public void testEnclosingInstanceValid(TestSource source) {
			assertTestClass(source.generateFileObject()).compilesWithoutError();
		}
	}

	@RunWith(Theories.class)
	public static class EnclosingInstanceInvalidIntegrationTest extends IntegrationTestBase {

		@DataPoint public static final TestSource INSTANCE_NESTED_CLASS = new TestSource(
				TEST_PACKAGE, TEST_CLASS, Modifier.PUBLIC).appendFields(FOO).innerClasses(
						new TestSource(TEST_CLASS + "Inner1", Modifier.PUBLIC).appendFields(BAR));

		@DataPoint public static final TestSource PRIVATE_CLASS = new TestSource(TEST_PACKAGE,
				TEST_CLASS, Modifier.PRIVATE).appendFields(FOO);

		@Theory
		public void testEnclosingInstanceInvalid(TestSource source) {
			assertTestClass(source.generateFileObject()).failsToCompile();
		}

	}

}
