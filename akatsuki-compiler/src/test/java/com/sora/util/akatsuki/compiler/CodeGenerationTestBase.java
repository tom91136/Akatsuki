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

package com.sora.util.akatsuki.compiler;

import android.os.Bundle;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.sora.util.akatsuki.Akatsuki;
import com.sora.util.akatsuki.BundleRetainer;
import com.sora.util.akatsuki.DeclaredConverter;
import com.sora.util.akatsuki.Internal;
import com.sora.util.akatsuki.RetainConfig;
import com.sora.util.akatsuki.RetainConfig.Optimisation;
import com.sora.util.akatsuki.Retained;
import com.sora.util.akatsuki.RetainerCache;
import com.sora.util.akatsuki.TransformationTemplate;
import com.sora.util.akatsuki.TransformationTemplate.Execution;
import com.sora.util.akatsuki.TransformationTemplate.StatementTemplate;
import com.sora.util.akatsuki.TypeConstraint;
import com.sora.util.akatsuki.TypeConstraint.Bound;
import com.sora.util.akatsuki.TypeConverter;
import com.sora.util.akatsuki.TypeFilter;
import com.sora.util.akatsuki.compiler.CodeGenerationTestBase.TestEnvironment.FieldFilter;
import com.sora.util.akatsuki.compiler.CompilerUtils.Result;
import com.sora.util.akatsuki.compiler.Field.RetainedField;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.FieldSpec.Builder;
import com.squareup.javapoet.TypeVariableName;

import org.junit.Assert;
import org.mockito.ArgumentCaptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public abstract class CodeGenerationTestBase extends TestBase {

	private static AtomicLong classIdentifier = new AtomicLong();

	@SuppressWarnings("unchecked")
	protected <T> Class<?> toArrayClass(Class<T> clazz) {
		return Array.newInstance(clazz, 0).getClass();
	}



	protected TestEnvironment testFieldHiding(RetainedField first, RetainedField second) {
		final JavaSource superClass = new JavaSource(TEST_PACKAGE, generateClassName(),
				Modifier.PUBLIC).fields(first.createFieldSpec());
		final JavaSource subclass = new JavaSource(TEST_PACKAGE,
				TEST_CLASS + "Client" + generateClassName(), Modifier.PUBLIC)
						.fields(second.createFieldSpec()).superClass(superClass);
		return new TestEnvironment(this, subclass, superClass);
	}

	// generates a class for each field, each class extends the previous class;
	// left most spec argument will be the top most class
	protected TestEnvironment testInheritance(boolean cache, Field first, Field... rest) {
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
					.builderTransformer(
							(builder,
									s) -> builder.addAnnotation(AnnotationSpec
											.builder(RetainConfig.class).addMember("optimisation",
													"$T.$L", Optimisation.class, Optimisation.NONE)
									.build()));
		}
		final TestEnvironment environment = new TestEnvironment(this, sources);
		environment.invokeSaveAndRestore();
		environment.testSaveRestoreInvocation(n -> true, TestEnvironment.CLASS, fields);
		return environment;
	}

	protected void testGenericType(Class<?>... input) {
		// we could make Field support <T> but that's too much effort for this
		// single use case
		final TypeVariableName typeVariable = TypeVariableName.get("T", input);
		final JavaSource source = new JavaSource(TEST_PACKAGE, generateClassName(), Modifier.PUBLIC)
				.fields(field(TypeVariableName.get("T"), "t", Retained.class))
				.builderTransformer((b, s) -> b.addTypeVariable(typeVariable));

		final TestEnvironment environment = new TestEnvironment(this, source);
		environment.invokeSaveAndRestore();

		verify(environment.mockedBundle, times(1)).putParcelable(eq("t"), any());
		verify(environment.mockedBundle, times(1)).getParcelable(eq("t"));
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

	protected TestEnvironment testTypes(Predicate<String> namePredicate,
			FieldFilter accessorTypeFilter, Function<Field, FieldSpec> fieldToSpec,
			Field... fields) {
		final HashSet<Field> set = Sets.newHashSet(fields);
		if (set.size() != fields.length)
			throw new IllegalArgumentException("Duplicate fields are not allowed");

		// mockito explodes if the classes are not public...
		final JavaSource source = new JavaSource("test", generateClassName(), Modifier.PUBLIC)
				.fields(set.stream()
						.map(f -> fieldToSpec != null ? fieldToSpec.apply(f)
								: field(f.typeName(), f.name, Retained.class))
						.collect(Collectors.toList()));
		final TestEnvironment environment = new TestEnvironment(this, source);
		environment.invokeSaveAndRestore();
		environment.testSaveRestoreInvocation(namePredicate, accessorTypeFilter, set);
		return environment;
	}

	public static String generateClassName() {
		return TEST_CLASS + classIdentifier.incrementAndGet();
	}

	public static class TestEnvironment {

		private final List<JavaSource> sources;

		public enum Accessor {
			PUT, GET
		}

		public interface FieldFilter {
			boolean test(Field field, Class<?> type, Type[] arguments);
		}

		final Object mockedSource;
		final Bundle mockedBundle;
		final BundleRetainer<Object> retainer;
		private final Result result;

		public TestEnvironment(TestBase base, List<JavaSource> sources) {
			this.sources = sources;
			try {
				result = CompilerUtils.compile(Thread.currentThread().getContextClassLoader(),
						base.processors(), sources.stream().map(JavaSource::generateFileObject)
								.toArray(JavaFileObject[]::new));
				if (result.compilationException != null)
					throw result.compilationException;
			} catch (Exception e) {
				throw new RuntimeException("Compilation was unsuccessful." + printAllSources(), e);
			}

			System.out.println(printAllSources());

			final Class<?> testClass;
			try {
				final String fqcn = sources.get(0).fqcn();
				testClass = result.classLoader.loadClass(fqcn);

				RetainerCache retainerCache = null;
				try {
					final Class<?> retainerCacheClass = result.classLoader.loadClass(
							Akatsuki.RETAINER_CACHE_PACKAGE + "." + Akatsuki.RETAINER_CACHE_NAME);
					retainerCache = (RetainerCache) retainerCacheClass.newInstance();
				} catch (Exception ignored) {
					// doesn't really matter
				}
				retainer = Internal.createRetainer(result.classLoader, retainerCache, fqcn,
						testClass);
				mockedSource = testClass.newInstance();
				mockedBundle = mock(Bundle.class);
			} catch (Exception e) {
				throw new RuntimeException(
						"Compilation was successful but an error occurred while setting up the test environment."
								+ printAllSources(),
						e);
			}
		}

		public TestEnvironment(TestBase base, JavaSource source, JavaSource... required) {
			this(base, Lists.asList(source, required));
		}

		public void invokeSave() {
			try {
				retainer.save(mockedSource, mockedBundle);
			} catch (Exception e) {
				throw new AssertionError("Unable to invoke save. " + printAllSources(), e);
			}

		}

		public void invokeRestore() {
			try {
				retainer.restore(mockedSource, mockedBundle);
			} catch (Exception e) {
				throw new AssertionError("Unable to invoke restore. " + printAllSources(), e);
			}
		}

		public void invokeSaveAndRestore() {
			invokeSave();
			invokeRestore();
		}

		public String printAllSources() {
			StringBuilder builder = new StringBuilder("\nCompiler Input:\n");
			final Pattern newLinePattern = Pattern.compile("\\r?\\n");
			for (JavaSource source : sources) {
				builder.append("Fully qualified name: ").append(source.fqcn()).append("\n");
				final String sourceCode = source.generateSource();
				final String[] lines = newLinePattern.split(sourceCode);
				String format = String.format("%%0%dd", String.valueOf(lines.length).length());
				for (int i = 0; i < lines.length; i++) {
					builder.append(String.format(format, i + 1)).append('.').append(lines[i]);
					if (i != lines.length - 1)
						builder.append("\n");
				}
				builder.append("\n===================\n");
			}
			if (result == null) {
				builder.append("No sources generated.");
			} else {
				builder.append(result.printGeneratedSources());
			}
			return builder.toString();
		}

		public void testSaveRestoreInvocation(Predicate<String> namePredicate,
				FieldFilter accessorTypeFilter, Set<Field> fields) {
			for (Accessor accessor : Accessor.values()) {
				executeTestCaseWithFields(fields, namePredicate, accessorTypeFilter, accessor);
			}
		}

		public void testSaveRestoreInvocation(Predicate<String> namePredicate,
				FieldFilter accessorTypeFilter, List<? extends Field> fields) {
			final HashSet<Field> set = Sets.newHashSet(fields);
			if (set.size() != fields.size())
				throw new IllegalArgumentException("Duplicate fields are not allowed");
			testSaveRestoreInvocation(namePredicate, accessorTypeFilter, set);
		}

		static FieldFilter ALWAYS = (f, t, a) -> true;
		static FieldFilter CLASS = (f, t, a) -> f.clazz.equals(t);
		static FieldFilter ASSIGNABLE = (f, t, a) -> t.isAssignableFrom(f.clazz);

		public static class AccessorKeyPair {
			public final String putKey;
			public final String getKey;

			public AccessorKeyPair(String putKey, String getKey) {
				this.putKey = putKey;
				this.getKey = getKey;
			}

			public void assertSameKeyUsed() {
				Assert.assertEquals("Same key expected", putKey, getKey);
			}

			public void assertNotTheSame(AccessorKeyPair another) {
				Assert.assertNotEquals("Different keys expected", putKey, another.putKey);
				Assert.assertNotEquals("Different keys expected", getKey, another.getKey);
			}

		}

		public AccessorKeyPair captureTestCaseKeysWithField(Field field,
				Predicate<String> methodNamePredicate, FieldFilter accessorTypeFilter) {
			return new AccessorKeyPair(
					captureTestCaseKeyWithField(field, methodNamePredicate, accessorTypeFilter,
							Accessor.PUT),
					captureTestCaseKeyWithField(field, methodNamePredicate, accessorTypeFilter,
							Accessor.GET));
		}

		public String captureTestCaseKeyWithField(Field field,
				Predicate<String> methodNamePredicate, FieldFilter accessorTypeFilter,
				Accessor accessor) {
			final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
			for (Method method : Bundle.class.getMethods()) {
				// check correct signature, name predicate and type
				if (!checkMethodIsAccessor(method, accessor)
						|| !methodNamePredicate.test(method.getName())
						|| !filterTypes(method, accessor, accessorTypeFilter, field))
					continue;
				try {
					if (accessor == Accessor.PUT) {
						method.invoke(verify(mockedBundle, atLeastOnce()), captor.capture(),
								any(field.clazz));
					} else {
						method.invoke(verify(mockedBundle, atLeastOnce()), captor.capture());
					}
					return captor.getValue();
				} catch (Exception e) {
					throw new AssertionError("Invocation of method " + method.getName()
							+ " on mocked object " + "failed." + printAllSources(), e);
				}
			}
			throw new RuntimeException(
					"No invocation caught for field: " + field.toString() + printAllSources());
		}

		private void executeTestCaseWithFields(Set<? extends Field> fieldList,
				Predicate<String> methodNamePredicate, FieldFilter accessorTypeFilter,
				Accessor accessor) {
			Set<Field> allFields = new HashSet<>(fieldList.stream()
					.filter(f -> f instanceof RetainedField).collect(Collectors.toList()));
			for (Method method : Bundle.class.getMethods()) {

				// check correct signature and name predicate
				if (!checkMethodIsAccessor(method, accessor)
						|| !methodNamePredicate.test(method.getName()))
					continue;

				// find methods who's accessor type matches the given fields
				List<Field> matchingField = allFields.stream()
						.filter(f -> filterTypes(method, accessor, accessorTypeFilter, f))
						.collect(Collectors.toList());

				// no signature match
				if (matchingField.isEmpty())
					continue;

				// more than one match, we should have exactly one match
				if (matchingField.size() > 1) {
					throw new AssertionError(method.toString() + " matches multiple field "
							+ fieldList + ", this is ambiguous and should not happen."
							+ printAllSources());
				}
				final Field field = matchingField.get(0);
				try {
					if (accessor == Accessor.PUT) {
						method.invoke(verify(mockedBundle, times(1)), eq(field.name),
								any(field.clazz));
					} else {
						method.invoke(verify(mockedBundle, times(1)), eq(field.name));
					}
					allFields.remove(field);

				} catch (Exception e) {
					throw new AssertionError("Invocation of method " + method.getName()
							+ " on mocked object " + "failed." + printAllSources(), e);
				}
			}
			if (!allFields.isEmpty())
				throw new RuntimeException("While testing for accessor:" + accessor
						+ " some fields are left untested because a suitable accessor cannot be found: "
						+ allFields + printAllSources());
		}

		private boolean filterTypes(Method method, Accessor accessor,
				FieldFilter accessorTypeFilter, Field field) {
			Parameter[] parameters = method.getParameters();
			Class<?> type = accessor == Accessor.PUT ? parameters[1].getType()
					: method.getReturnType();
			Type[] arguments = {};
			final Type genericType = accessor == Accessor.PUT ? parameters[1].getParameterizedType()
					: method.getGenericReturnType();
			if (genericType instanceof ParameterizedType) {
				// if field is not generic while accessor type is, bail
				if (!field.generic()) {
					return false;
				}
				// or else record the type argument for the filter
				arguments = ((ParameterizedType) genericType).getActualTypeArguments();
			}
			return accessorTypeFilter.test(field, type, arguments);
		}

		private boolean checkMethodIsAccessor(Method method, Accessor accessor) {
			// Bundle accessor format:
			// put<Suffix>(String key, <Type>) : void
			// get<Suffix>(String key) : <Type>
			// the following are strictly obeyed
			// first parameter will always be a string
			// getter has 1 argument, setter has 2
			// must start with "put" for getter ans "set" for setter
			// <Suffix> cannot be empty
			final String name = method.getName();
			boolean correctSignature = name.startsWith(accessor.name().toLowerCase())
					&& name.length() > accessor.name().length()
					&& method.getParameterCount() == (accessor == Accessor.PUT ? 2 : 1);
			if (!correctSignature)
				return false;
			final Parameter[] parameters = method.getParameters();
			return parameters[0].getType().equals(String.class);
		}

	}

}
