package com.sora.util.akatsuki;

import javax.lang.model.element.Modifier;

import org.junit.experimental.runners.Enclosed;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import com.squareup.javapoet.FieldSpec;

@RunWith(Enclosed.class)
public class EnclosingInstanceValidationTest extends TestBase {

	public static final FieldSpec FOO = field(STRING_TYPE, "foo", Retained.class);
	public static final FieldSpec BAR = field(STRING_TYPE, "bar", Retained.class);

	@RunWith(Theories.class)
	public static class EnclosingInstanceValidTest extends TestBase {

		@DataPoint public static final JavaSource STATIC_NESTED_CLASS = new JavaSource(TEST_PACKAGE,
				TEST_CLASS, Modifier.PUBLIC).fields(FOO).innerClasses(
						new JavaSource(TEST_CLASS + "Inner1", Modifier.PUBLIC, Modifier.STATIC)
								.fields(BAR));

		@DataPoint public static final JavaSource STATIC_MULTIPLE_NESTED_CLASS = new JavaSource(
				TEST_PACKAGE, TEST_CLASS, Modifier.PUBLIC).fields(FOO).innerClasses(
						new JavaSource(TEST_CLASS + "Inner1", Modifier.PUBLIC, Modifier.STATIC)
								.fields(BAR),
						new JavaSource(TEST_CLASS + "Inner2", Modifier.PUBLIC, Modifier.STATIC)
								.fields(BAR));

		@DataPoint public static final JavaSource PACKAGE_PRIVATE_CLASS = new JavaSource(
				TEST_PACKAGE, TEST_CLASS).fields(FOO);

		@Theory
		public void testEnclosingInstanceValid(JavaSource source) {
			assertTestClass(source.generateFileObject()).compilesWithoutError();
		}
	}

	@RunWith(Theories.class)
	public static class EnclosingInstanceInvalidTest extends TestBase {

		@DataPoint public static final JavaSource INSTANCE_NESTED_CLASS = new JavaSource(
				TEST_PACKAGE, TEST_CLASS, Modifier.PUBLIC).fields(FOO).innerClasses(
						new JavaSource(TEST_CLASS + "Inner1", Modifier.PUBLIC).fields(BAR));

		@DataPoint public static final JavaSource PRIVATE_CLASS = new JavaSource(TEST_PACKAGE,
				TEST_CLASS, Modifier.PRIVATE).fields(FOO);

		@Theory
		public void testEnclosingInstanceInvalid(JavaSource source) {
			assertTestClass(source.generateFileObject()).failsToCompile();
		}

	}

}
