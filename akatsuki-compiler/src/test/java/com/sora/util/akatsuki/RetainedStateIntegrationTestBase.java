/*
 * Copyright 2015 WEI CHEN LIN
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sora.util.akatsuki;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.lang.model.element.Modifier;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.sora.util.akatsuki.RetainedStateTestEnvironment.BundleRetainerTester;
import com.sora.util.akatsuki.RetainedStateTestEnvironment.FieldFilter;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeVariableName;

public abstract class RetainedStateIntegrationTestBase extends IntegrationTestBase {

	static final Predicate<String> NEVER = s -> false;

	static final Predicate<String> ALWAYS = s -> true;

	@SuppressWarnings("unchecked")
	protected <T> Class<?> toArrayClass(Class<T> clazz) {
		return Array.newInstance(clazz, 0).getClass();
	}

	protected RetainedStateTestEnvironment testFieldHiding(RetainedTestField first,
			RetainedTestField second) {
		final TestSource superClass = new TestSource(TEST_PACKAGE, generateClassName(),
				Modifier.PUBLIC).appendFields(first.createFieldSpec());
		final TestSource subclass = new TestSource(TEST_PACKAGE,
				TEST_CLASS + "Client" + generateClassName(), Modifier.PUBLIC)
						.appendFields(second.createFieldSpec()).superClass(superClass);
		return new RetainedStateTestEnvironment(this, subclass, superClass);
	}

	/**
	 * Generates a class for each field, each class extends the previous class;
	 * left most spec argument will be the top most class
	 */
	protected RetainedStateTestEnvironment testInheritance(boolean cache, TestField first,
			TestField... rest) {
		final List<TestField> fields = Lists.asList(first, rest);
		TestSource lastClass = null;
		List<TestSource> sources = new ArrayList<>();
		for (TestField field : fields) {
			final TestSource clazz = new TestSource(TEST_PACKAGE, generateClassName(),
					Modifier.PUBLIC).appendFields(field.createFieldSpec());
			if (lastClass != null)
				lastClass.superClass(clazz);
			lastClass = clazz;
			sources.add(clazz);
		}
		if (!cache) {
			sources.get(0).appendTransformation((builder, s) -> builder.addAnnotation(AnnotationSpec
					.builder(AkatsukiConfig.class).addMember("optFlags", "{}").build()));
		}
		final RetainedStateTestEnvironment environment = new RetainedStateTestEnvironment(this,
				sources);
		environment.tester().invokeSaveAndRestore();
		environment.tester()
				.testSaveRestoreInvocation(ALWAYS, BundleRetainerTester.CLASS_EQ,
						fields.stream().filter(tf -> tf instanceof RetainedTestField)
								.map(tf -> (RetainedTestField) tf).collect(Collectors.toList()),
						f -> 1);
		return environment;
	}

	protected void testGenericType(String parameterName, Class<?>... input) {
		// we could make Field support <T> but that's too much effort for this
		// single use case

		String fieldName = parameterName.toLowerCase(Locale.ENGLISH);
		final TypeVariableName typeVariable = TypeVariableName.get(parameterName, input);
		FieldSpec field = field(TypeVariableName.get(parameterName), fieldName, Retained.class);
		final TestSource source = new TestSource(TEST_PACKAGE, generateClassName(), Modifier.PUBLIC)
				.appendFields(field)
				.appendTransformation((b, s) -> b.addTypeVariable(typeVariable));
		final RetainedStateTestEnvironment environment = new RetainedStateTestEnvironment(this,
				source);
		environment.tester().invokeSaveAndRestore();
		environment.tester().testSaveRestoreInvocation(name -> true, BundleRetainerTester.CLASS_EQ,
				Collections.singletonList(new RetainedTestField(input[0], fieldName)), f -> 1);

	}

	protected void testSimpleTypes(Predicate<String> namePredicate, FieldFilter accessorTypeFilter,
			Function<RetainedTestField, RetainedTestField> transform, Class<?>... differentTypes) {
		testTypes(namePredicate, accessorTypeFilter, f -> 1,
				mapToTestField(transform, differentTypes));
	}

	protected void testNoInvocation(Predicate<String> namePredicate, FieldFilter accessorTypeFilter,
			Function<RetainedTestField, RetainedTestField> transform,
			Function<TestField, Integer> times, Class<?>... differentTypes) {
		testTypes(namePredicate, accessorTypeFilter, times,
				mapToTestField(transform, differentTypes));
	}

	private static RetainedTestField[] mapToTestField(
			Function<RetainedTestField, RetainedTestField> transform, Class<?>... differentTypes) {
		return Arrays.stream(differentTypes).map(RetainedTestField::new).map(transform)
				.toArray(RetainedTestField[]::new);
	}

	protected void testParameterizedTypes(Predicate<String> namePredicate,
			FieldFilter accessorTypeFilter, Class<?> rawType, Class<?>... firstArgumentTypes) {
		for (Class<?> types : firstArgumentTypes) {
			testTypes(namePredicate, accessorTypeFilter, f -> 1,
					new RetainedTestField(rawType, types));
		}
	}

	protected RetainedStateTestEnvironment testTypes(Predicate<String> namePredicate,
			FieldFilter accessorTypeFilter, Function<TestField, Integer> times,
			RetainedTestField... fields) {
		final HashSet<TestField> set = Sets.newHashSet(fields);
		if (set.size() != fields.length)
			throw new IllegalArgumentException("Duplicate fields are not allowed");

		final RetainedStateTestEnvironment environment = createTestEnvironment(fields);

		environment.tester().invokeSaveAndRestore();
		environment.tester().testSaveRestoreInvocation(namePredicate, accessorTypeFilter,
				Sets.newHashSet(fields), times);
		return environment;
	}

	protected RetainedStateTestEnvironment createTestEnvironment(RetainedTestField... fields) {
		// mockito explodes if the classes are not public...
		final TestSource source = new TestSource("testRetain", generateClassName(), Modifier.PUBLIC)
				.appendTestFields(fields);
		return new RetainedStateTestEnvironment(this, source);
	}
}
