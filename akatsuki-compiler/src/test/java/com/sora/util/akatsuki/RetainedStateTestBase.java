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
import com.sora.util.akatsuki.RetainConfig.Optimisation;
import com.sora.util.akatsuki.RetainedStateTestEnvironment.BundleRetainerTester;
import com.sora.util.akatsuki.RetainedStateTestEnvironment.FieldFilter;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeVariableName;

public abstract class RetainedStateTestBase extends TestBase {

	@SuppressWarnings("unchecked")
	protected <T> Class<?> toArrayClass(Class<T> clazz) {
		return Array.newInstance(clazz, 0).getClass();
	}

	protected RetainedStateTestEnvironment testFieldHiding(RetainedField first,
			RetainedField second) {
		final JavaSource superClass = new JavaSource(TEST_PACKAGE, generateClassName(),
				Modifier.PUBLIC).fields(first.createFieldSpec());
		final JavaSource subclass = new JavaSource(TEST_PACKAGE,
				TEST_CLASS + "Client" + generateClassName(), Modifier.PUBLIC)
						.fields(second.createFieldSpec()).superClass(superClass);
		return new RetainedStateTestEnvironment(this, subclass, superClass);
	}

	// generates a class for each field, each class extends the previous class;
	// left most spec argument will be the top most class
	protected RetainedStateTestEnvironment testInheritance(boolean cache, Field first,
			Field... rest) {
		final List<Field> fields = Lists.asList(first, rest);
		JavaSource lastClass = null;
		List<JavaSource> sources = new ArrayList<>();
		for (Field field : fields) {
			final JavaSource clazz = new JavaSource(TEST_PACKAGE, generateClassName(),
					Modifier.PUBLIC).fields(field.createFieldSpec());
			if (lastClass != null)
				lastClass.superClass(clazz);
			lastClass = clazz;
			sources.add(clazz);
		}
		if (!cache) {
			sources.get(0)
					.appendTransformation((builder, s) -> builder.addAnnotation(AnnotationSpec
							                                                            .builder(RetainConfig.class).addMember("optimisation", "$T.$L", Optimisation.class, Optimisation.NONE).build()));
		}
		final RetainedStateTestEnvironment environment = new RetainedStateTestEnvironment(this,
				sources);
		environment.tester().invokeSaveAndRestore();
		environment.tester().testSaveRestoreInvocation(n -> true, BundleRetainerTester.CLASS,
				fields);
		return environment;
	}

	protected void testGenericType(String parameterName, Class<?>... input) {
		// we could make Field support <T> but that's too much effort for this
		// single use case

		String fieldName = parameterName.toLowerCase(Locale.ENGLISH);
		final TypeVariableName typeVariable = TypeVariableName.get(parameterName, input);
		FieldSpec field = field(TypeVariableName.get(parameterName), fieldName, Retained.class);
		final JavaSource source = new JavaSource(TEST_PACKAGE, generateClassName(), Modifier.PUBLIC)
				.fields(field).appendTransformation((b, s) -> b.addTypeVariable(typeVariable));
		final RetainedStateTestEnvironment environment = new RetainedStateTestEnvironment(this,
				source);
		environment.tester().invokeSaveAndRestore();
		environment.tester().testSaveRestoreInvocation(name -> true, BundleRetainerTester.CLASS,
				Collections.singletonList(new Field(input[0], fieldName)));

	}

	protected void testSimpleTypes(Predicate<String> namePredicate, FieldFilter accessorTypeFilter,
			Function<Field, FieldSpec> fieldToSpec, Class<?>... differentTypes) {
		Field[] fields = Arrays.stream(differentTypes).map(c -> new Field(c)).toArray(Field[]::new);
		testTypes(namePredicate, accessorTypeFilter, fieldToSpec, fields);
	}

	protected void testParameterizedTypes(Predicate<String> namePredicate,
			FieldFilter accessorTypeFilter, Function<Field, FieldSpec> fieldToSpec,
			Class<?> rawType, Class<?>... firstArgumentTypes) {
		for (Class<?> types : firstArgumentTypes) {
			testTypes(namePredicate, accessorTypeFilter, fieldToSpec, new Field(rawType, types));
		}
	}

	protected RetainedStateTestEnvironment testTypes(Predicate<String> namePredicate,
			FieldFilter accessorTypeFilter, Function<Field, FieldSpec> fieldToSpec,
			Field... fields) {
		final HashSet<Field> set = Sets.newHashSet(fields);
		if (set.size() != fields.length)
			throw new IllegalArgumentException("Duplicate fields are not allowed");

		// mockito explodes if the classes are not public...
		final JavaSource source = new JavaSource("testRetain", generateClassName(), Modifier.PUBLIC)
				.fields(set.stream()
						.map(f -> fieldToSpec != null ? fieldToSpec.apply(f)
								: field(f.typeName(), f.name, Retained.class))
						.collect(Collectors.toList()));
		final RetainedStateTestEnvironment environment = new RetainedStateTestEnvironment(this,
				source);
		environment.tester().invokeSaveAndRestore();
		environment.tester().testSaveRestoreInvocation(namePredicate, accessorTypeFilter, set);
		return environment;
	}

}
