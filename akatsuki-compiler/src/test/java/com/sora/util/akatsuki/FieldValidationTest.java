package com.sora.util.akatsuki;

import java.io.IOException;
import java.util.List;

import javax.lang.model.element.Modifier;

import org.junit.experimental.runners.Enclosed;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.FieldSpec;

@RunWith(Enclosed.class)
public class FieldValidationTest {

	@RunWith(Theories.class)
	public static class FieldValidTest extends TestBase {

		@DataPoint public static final ImmutableList<FieldSpec> PACKAGE_PRIVATE_FIELD = ImmutableList
				.of(field(STRING_TYPE, "a", Retained.class));

		@DataPoint public static final ImmutableList<FieldSpec> PUBLIC_FIELD = ImmutableList
				.of(field(STRING_TYPE, "b", Retained.class, Modifier.PUBLIC));

		@DataPoint public static final ImmutableList<FieldSpec> VALID_FIELDS = ImmutableList
				.<FieldSpec> builder().addAll(PACKAGE_PRIVATE_FIELD).addAll(PUBLIC_FIELD).build();

		@Theory
		public void testValidField(List<FieldSpec> specs) throws IOException {
			CodeGenUtils.testField(specs).processedWith(processors()).compilesWithoutError();
		}
	}

	@RunWith(Theories.class)
	public static class FieldInvalidTest extends TestBase {

		@DataPoint public static final ImmutableList<FieldSpec> STATIC_FIELD = ImmutableList
				.of(field(STRING_TYPE, "a", Retained.class, Modifier.STATIC));
		@DataPoint public static final ImmutableList<FieldSpec> FINAL_FIELD = ImmutableList
				.of(field(STRING_TYPE, "b", Retained.class, "\"b\"", Modifier.FINAL));
		@DataPoint public static final ImmutableList<FieldSpec> PRIVATE_FIELD = ImmutableList
				.of(field(STRING_TYPE, "d", Retained.class, Modifier.PRIVATE));

		@DataPoint public static final ImmutableList<FieldSpec> INVALID_MODIFIERS_1 = ImmutableList
				.of(field(STRING_TYPE, "e", Retained.class, Modifier.PRIVATE, Modifier.STATIC));

		@DataPoint public static final ImmutableList<FieldSpec> INVALID_MODIFIERS_2 = ImmutableList
				.of(field(STRING_TYPE, "f", Retained.class, "\"f\"", Modifier.TRANSIENT,
						Modifier.FINAL));

		@DataPoint public static final ImmutableList<FieldSpec> INVALID_FIELDS = ImmutableList
				.<FieldSpec> builder().addAll(STATIC_FIELD).addAll(FINAL_FIELD)
				.addAll(PRIVATE_FIELD).addAll(INVALID_MODIFIERS_1).addAll(INVALID_MODIFIERS_2)
				.build();

		@Theory
		public void testInvalidField(List<FieldSpec> specs) throws IOException {
			CodeGenUtils.testField(specs).processedWith(processors()).failsToCompile();
		}

	}
}
