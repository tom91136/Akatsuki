package com.sora.util.akatsuki;

import static com.sora.util.akatsuki.RetainedStateTestEnvironment.BundleRetainerTester.CLASS_EQ;

import java.util.Arrays;

import javax.lang.model.element.Modifier;

import org.junit.Test;

import com.sora.util.akatsuki.Retained.RestorePolicy;
import com.sora.util.akatsuki.RetainedStateTestEnvironment.BundleRetainerTester;
import com.squareup.javapoet.AnnotationSpec;

public class RetainedAnnotationIntegrationTest extends RetainedStateIntegrationTestBase {

	@Test
	public void testRestorePolicyOverwrite() {
		testSimpleTypes(ALWAYS, CLASS_EQ, rtf -> rtf.policy(RestorePolicy.OVERWRITE),
				SupportedTypeIntegrationTest.SUPPORTED_SIMPLE_CLASSES);
	}

	@Test
	public void testRestorePolicyIfNull() {
		testSimpleTypes(ALWAYS, CLASS_EQ, rtf -> rtf.policy(RestorePolicy.IF_NULL),
				SupportedTypeIntegrationTest.SUPPORTED_SIMPLE_CLASSES);
	}

	@Test
	public void testRestorePolicyIfNotNull() {
		// no method should match
		testNoInvocation(ALWAYS, CLASS_EQ, rtf -> rtf.policy(RestorePolicy.IF_NOT_NULL), f -> 0,
				SupportedTypeIntegrationTest.SUPPORTED_SIMPLE_CLASSES);
	}

	@Test
	public void testRestorePolicyShouldNotReactToPrimitives() {
		testSimpleTypes(ALWAYS, CLASS_EQ, rtf -> rtf.policy(RestorePolicy.IF_NULL),
				SupportedTypeIntegrationTest.PRIMITIVES);
	}

	@Test
	public void testSkipSingle() {
		Class<?> skippedClass = SupportedTypeIntegrationTest.SUPPORTED_SIMPLE_CLASSES[0];
		RetainedTestField[] fields = Arrays.stream(SupportedTypeIntegrationTest.SUPPORTED_SIMPLE_CLASSES)
				.map(c -> new RetainedTestField(c).skip(c == skippedClass))
				.toArray(RetainedTestField[]::new);
		testTypes(ALWAYS, CLASS_EQ, f -> f.clazz != skippedClass ? 1 : 0, fields);

	}

	@Test
	public void testSkipAll() {
		testNoInvocation(ALWAYS, BundleRetainerTester.CLASS_EQ, rtf -> rtf.skip(true), f -> 0,
				SupportedTypeIntegrationTest.SUPPORTED_SIMPLE_CLASSES);
	}

	// TODO not for now, maybe later, implementing this would require changing
	// the test base
	// @Test
	// public void testSkipAllShouldNotGenerateAnyClasses() {
	// RetainedStateTestEnvironment environment = testTypes(NEVER,
	// BundleRetainerTester.NEVER, 1,
	// Arrays.stream(SupportedTypeIntegrationTest.SUPPORTED_SIMPLE_CLASSES)
	// .map(c -> new RetainedTestField(c).skip(true))
	// .toArray(RetainedTestField[]::new));
	// ImmutableList<JavaFileObject> sources = environment.generatedSources();
	// assertTrue("Unexpected class generated: " + sources, sources.isEmpty());
	//
	// }

	@Test(expected = RuntimeException.class)
	public void testTransientShouldSkip() {
		testSimpleTypes(NEVER, CLASS_EQ, rtf -> {
			rtf.appendModifier(Modifier.TRANSIENT);
			return rtf;
		} , String.class);
	}

	private AnnotationSpec fromRestorePolicy(RestorePolicy policy) {
		return AnnotationSpec.builder(Retained.class)
				.addMember("restorePolicy", "$T.$L", RestorePolicy.class, policy).build();
	}

}
