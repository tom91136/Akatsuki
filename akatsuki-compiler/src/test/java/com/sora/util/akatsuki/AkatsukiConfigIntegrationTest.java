package com.sora.util.akatsuki;

import javax.lang.model.element.Modifier;

import org.junit.Test;

import android.app.Fragment;
import android.os.Bundle;

import com.sora.util.akatsuki.AkatsukiConfig.Flags;
import com.squareup.javapoet.AnnotationSpec;

public class AkatsukiConfigIntegrationTest extends IntegrationTestBase {

	@Test(expected = RuntimeException.class)
	public void testMultipleConfigShouldNotCompile() {

		TestSource a = new TestSource(TEST_PACKAGE, generateClassName(), Modifier.PUBLIC)
				.appendTransformation((b, s) -> b
						.addAnnotation(AnnotationSpec.builder(AkatsukiConfig.class).build()));

		String name = generateClassName();
		TestSource b = new TestSource(TEST_PACKAGE, name, Modifier.PUBLIC,
				Modifier.ABSTRACT)
						.appendTestFields(new RetainedTestField(String.class),
								new ArgTestField(
										Bundle.class))
						.appendTransformation((builder,
								testSource) -> builder.addAnnotation(
										AnnotationSpec.builder(AkatsukiConfig.class).build())
								.superclass(Fragment.class));

		new SimpleTestEnvironment(this, b, a);
	}

	@Test
	public void testNoFilesGeneratedWhenDisabled() {
		AnnotationSpec spec = AnnotationSpec.builder(AkatsukiConfig.class)
				.addMember("flags", "$T.$L", Flags.class, Flags.DISABLE_COMPILER).build();

		String name = generateClassName();
		TestSource source = new TestSource(TEST_PACKAGE, name, Modifier.PUBLIC, Modifier.ABSTRACT)
				.appendTestFields(new RetainedTestField(String.class),
						new ArgTestField(Bundle.class))
				.appendTransformation((builder, testSource) -> builder.addAnnotation(spec)
						.superclass(Fragment.class));

		new SimpleTestEnvironment(this, source);
	}

	static class SimpleTestEnvironment extends BaseTestEnvironment {

		public SimpleTestEnvironment(IntegrationTestBase base, TestSource source, TestSource... required) {
			super(base, source, required);
		}

		@Override
		protected void setupTestEnvironment() throws Exception {
			// nah
		}
	}

	// private void assertExceptionThrown(TestSource first, TestSource... rest)
	// {
	// Throwable retainedStateException = null;
	// Throwable builderException = null;
	//
	// try {
	// new RetainedStateTestEnvironment(this, first,
	// rest).setupTestEnvironment();
	// } catch (Throwable e) {
	// retainedStateException = e;
	// }
	// try {
	// new BuilderTestEnvironment(this, first, rest).setupTestEnvironment();
	// } catch (Throwable e) {
	// builderException = e;
	// }
	//
	// assertNotNull(retainedStateException);
	// assertNotNull(builderException);
	// }
}
