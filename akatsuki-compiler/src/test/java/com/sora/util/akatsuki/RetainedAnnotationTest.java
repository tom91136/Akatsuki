package com.sora.util.akatsuki;

import java.io.Serializable;

import javax.lang.model.element.Modifier;

import org.junit.Test;

import com.sora.util.akatsuki.Retained.RestorePolicy;
import com.squareup.javapoet.AnnotationSpec;

public class RetainedAnnotationTest extends CodeGenerationTestBase {

	@Test
	public void testRestorePolicyOverwrite() {
		testSimpleTypes(n -> true, TestEnvironment.CLASS,
				f -> field(f.typeName(), f.name, fromRestorePolicy(RestorePolicy.OVERWRITE)),
				String.class);
	}

	@Test
	public void testRestorePolicyIfNull() {
		testSimpleTypes(n -> true, TestEnvironment.CLASS,
				f -> field(f.typeName(), f.name, fromRestorePolicy(RestorePolicy.IF_NULL)),
				String.class);
	}

	@Test
	public void testRestorePolicyIfNotNull() {
		// no method should match
		testSimpleTypes(n -> false, TestEnvironment.CLASS,
				f -> field(f.typeName(), f.name, fromRestorePolicy(RestorePolicy.IF_NOT_NULL)),
				String.class);
	}

	@Test
	public void testRestorePolicyShouldNotReactToPrimitives() {
		testSimpleTypes(n -> true, TestEnvironment.CLASS,
				f -> field(f.typeName(), f.name, fromRestorePolicy(RestorePolicy.IF_NULL)),
				int.class);
	}

	@Test
	public void testSkipSingle() {
		testSimpleTypes(n -> false, TestEnvironment.CLASS,
				f -> field(f.typeName(), f.name,
						AnnotationSpec.builder(Retained.class)
								.addMember("skip", "$L", f.clazz == String.class).build()),
				String.class, Serializable.class);
	}

	@Test()
	public void testSkipAll() {
		AnnotationSpec spec = AnnotationSpec.builder(Retained.class).addMember("skip", "$L", true)
				.build();
		testSimpleTypes(n -> false, TestEnvironment.CLASS, f -> field(f.typeName(), f.name, spec),
				String.class);
	}

	@Test(expected = RuntimeException.class)
	public void testTransientShouldSkip() {
		AnnotationSpec spec = AnnotationSpec.builder(Retained.class).build();
		testSimpleTypes(n -> false, TestEnvironment.CLASS,
				f -> field(f.typeName(), f.name, spec, Modifier.TRANSIENT), String.class);
	}

	private AnnotationSpec fromRestorePolicy(RestorePolicy policy) {
		return AnnotationSpec.builder(Retained.class)
				.addMember("restorePolicy", "$T.$L", RestorePolicy.class, policy).build();
	}

}
