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

import android.accounts.Account;
import android.app.Activity;
import android.app.Notification;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;
import android.location.Location;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.text.Editable;
import android.text.Spannable;
import android.text.Spanned;
import android.util.Size;
import android.util.SizeF;
import android.util.SparseArray;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Primitives;
import com.sora.util.akatsuki.Akatsuki;
import com.sora.util.akatsuki.BundleRetainer;
import com.sora.util.akatsuki.Retained;
import com.sora.util.akatsuki.compiler.CodeGenerationTest.TestEnvironment.AccessorKeyPair;
import com.sora.util.akatsuki.compiler.CodeGenerationTest.TestEnvironment.FieldFilter;
import com.sora.util.akatsuki.compiler.CompilerUtils.Result;
import com.sora.util.akatsuki.compiler.Field.RetainedField;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeVariableName;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class CodeGenerationTest extends TestBase {

	public static class MySerializable implements Serializable {

	}

	public static final Class<?>[] PRIMITIVES = new Class<?>[] { boolean.class, byte.class,
			short.class, int.class, long.class, char.class, float.class, double.class };

	// these classes are just some random parcelable subclasses chosen
	// based on intuition :)
	@SuppressWarnings("unchecked") public static final Class<? extends Parcelable>[] PARCELABLES_CLASSES = new Class[] {
			Parcelable.class, Account.class, Location.class, Bitmap.class, Intent.class,
			Notification.class, Point.class, PointF.class };

	public static final Class<?>[] SUPPORTED_ARRAY_CLASSES = { Parcelable[].class,
			CharSequence[].class, String[].class };

	public static final Class<?>[] SUPPORTED_SIMPLE_CLASSES = { Size.class, SizeF.class,
			String.class, CharSequence.class, IBinder.class, Bundle.class, Serializable.class };

	public static final ImmutableMap<Class<?>, Class<?>> SUPPORTED_SIMPLE_SUBCLASSES_MAP = ImmutableMap
			.<Class<?>, Class<?>> builder().put(StringBuilder.class, CharSequence.class)
			.put(Spannable.class, CharSequence.class).put(Binder.class, IBinder.class)
			.put(MySerializable.class, Serializable.class).build();

	public static AtomicLong classIdentifier = new AtomicLong();

	public static String generateClassName() {
		return TEST_CLASS + classIdentifier.incrementAndGet();
	}

	@Test(expected = RuntimeException.class)
	public void testUnsupportedType() {
		final JavaSource source = new JavaSource(TEST_PACKAGE, generateClassName(), Modifier.PUBLIC)
				.fields(field(ClassName.get(Activity.class), "badType", Retained.class));
		new TestEnvironment(this, source);
	}

	@Test
	public void testPrimitives() {
		testSimpleTypes(t -> true, TestEnvironment.CLASS, null, PRIMITIVES);
	}

	// TODO we got some wicked problem with boxed types, the compiled retainer
	// throws NPE at lines that doesn't even contain code
	@Ignore
	@Test
	public void testBoxedPrimitives() {
		final Class<?>[] classes = Arrays.stream(PRIMITIVES).map(Primitives::wrap)
				.toArray(Class<?>[]::new);
		// boxed primitives cannot be null otherwise we get NPE when unboxing
		ImmutableMap<Class<?>, String> defaultValueMap = ImmutableMap.<Class<?>, String> builder()
				.put(Byte.class, "0").put(Short.class, "0").put(Integer.class, "0")
				.put(Long.class, "0L").put(Float.class, "0.0F").put(Double.class, "0.0D")
				.put(Character.class, "\'a\'").put(Boolean.class, "false").build();
		testSimpleTypes(t -> true, TestEnvironment.CLASS, f -> field(f.typeName(), f.name,
				Retained.class, defaultValueMap.getOrDefault(f.clazz, null)), classes);

	}

	@Test
	public void testPrimitiveArrays() {
		final Class<?>[] classes = Arrays.stream(PRIMITIVES).map(this::toArrayClass)
				.toArray(Class<?>[]::new);
		testSimpleTypes(n -> n.contains("Array"), TestEnvironment.CLASS, null, classes);
	}

	@Test
	public void testSupportedSimpleTypes() {
		testSimpleTypes(n -> true, TestEnvironment.CLASS, null, SUPPORTED_SIMPLE_CLASSES);
	}

	@Test
	public void testSubclassOfSupportedTypes() {
		for (Entry<Class<?>, Class<?>> entry : SUPPORTED_SIMPLE_SUBCLASSES_MAP.entrySet()) {
			testSimpleTypes(n -> true, (f, t, a) -> t.equals(entry.getValue()), null,
					entry.getKey());
		}
	}

	@Test
	public void testParcelableAndParcelableSubclassTypes() {
		// parcelable requires special care because the get accessor returns a
		// <T> instead of Parcelable
		for (Class<? extends Parcelable> type : PARCELABLES_CLASSES) {
			// filter out the method [get|set]Parcelable and work with that only
			testSimpleTypes(n -> n.endsWith("Parcelable"), TestEnvironment.ALWAYS, null, type);
		}
	}

	@Test
	public void testSupportedArrayTypes() {
		testSimpleTypes(n -> n.contains("Array"), TestEnvironment.CLASS, null,
				SUPPORTED_ARRAY_CLASSES);
	}

	@Test
	public void testSupportedArraySubclassTypes() {
		List<Class<?>> classes = new ArrayList<>();
		classes.addAll(Arrays.asList(PARCELABLES_CLASSES));
		classes.addAll(Arrays.asList(StringBuilder.class, CharBuffer.class, Spannable.class,
				Editable.class, Spanned.class));

		for (Class<?> type : classes) {
			// the type of the accessor must be assignable to the field's
			testSimpleTypes(n -> n.contains("Array"), TestEnvironment.ASSIGNABLE, null,
					toArrayClass(type));
		}
	}

	@Test
	public void testSparseArrayParcelableType() {
		testParameterizedTypes(n -> n.contains("SparseParcelableArray"), TestEnvironment.CLASS,
				null, SparseArray.class, PARCELABLES_CLASSES);
	}

	@Test
	public void testParcelableArrayListType() {
		// parcelable arraylist also requires special care because the generic
		// argument of the setter is a wildcard (<? extends Parcelable>)
		testParameterizedTypes(n -> n.contains("ParcelableArrayList"), TestEnvironment.ALWAYS, null,
				ArrayList.class, PARCELABLES_CLASSES);

	}

	@Test
	public void testSupportedArrayListTypes() {
		testParameterizedTypes(n -> n.contains("ArrayList"),
				(f, t, a) -> t.equals(ArrayList.class) && Arrays.equals(a, f.parameters), null,
				ArrayList.class, String.class, Integer.class, CharSequence.class);

	}

	@Test
	public void testSimpleInheritance() {
		testInheritance(new RetainedField(String.class, "a"), new RetainedField(int.class, "b"));
	}

	@Test
	public void testMultiLevelInheritance() {
		testInheritance(new RetainedField(String.class, "a"), new RetainedField(int.class, "b"),
				new RetainedField(long.class, "c"));
	}

	@Test
	public void testMultiLevelInheritanceWithGap() {
		testInheritance(new RetainedField(String.class, "a"), new Field(int.class, "b"),
				new RetainedField(long.class, "c"));
	}

	@Test
	public void testInheritanceWithoutAnnotations() {
		testInheritance(new Field(int.class, "a"), new RetainedField(String.class, "b"));
	}

	@Test
	public void testFieldHiding() {
		final RetainedField first = new RetainedField(String.class, "a");
		final RetainedField second = new RetainedField(int.class, "a");
		final TestEnvironment environment = testFieldHiding(first, second);
		environment.invokeSaveAndRestore();
		final AccessorKeyPair firstKeys = environment.captureTestCaseKeysWithField(first, n -> true,
				TestEnvironment.CLASS);
		final AccessorKeyPair secondKeys = environment.captureTestCaseKeysWithField(second,
				n -> true, TestEnvironment.CLASS);
		firstKeys.assertSameKeyUsed();
		secondKeys.assertSameKeyUsed();
		firstKeys.assertNotTheSame(secondKeys);
	}

	@Test
	public void testGenericType()
			throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		testGenericType(Parcelable.class);
	}

	@Test
	public void testIntersectionType()
			throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		testGenericType(Parcelable.class, Serializable.class);
	}

	@SuppressWarnings("unchecked")
	private <T> Class<?> toArrayClass(Class<T> clazz) {
		return Array.newInstance(clazz, 0).getClass();
	}

	@SuppressWarnings("unchecked")
	private static <T> T newInstance(Class<?> clazz)
			throws IllegalAccessException, InstantiationException {
		return (T) clazz.newInstance();
	}

	private TestEnvironment testFieldHiding(RetainedField first, RetainedField second) {
		final JavaSource superClass = new JavaSource(TEST_PACKAGE, generateClassName(),
				Modifier.PUBLIC).fields(first.fieldSpecBuilder().build());
		final JavaSource subclass = new JavaSource(TEST_PACKAGE,
				TEST_CLASS + "Client" + generateClassName(), Modifier.PUBLIC)
						.fields(second.fieldSpecBuilder().build()).superClass(superClass);
		return new TestEnvironment(this, subclass, superClass);
	}

	// generates a class for each field, each class extends the previous class;
	// left most spec argument will be the top most class
	private TestEnvironment testInheritance(Field first, Field... rest) {
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
		final TestEnvironment environment = new TestEnvironment(this, sources);
		environment.invokeSaveAndRestore();
		environment.testSaveRestoreInvocation(n -> true, TestEnvironment.CLASS, fields);
		return environment;
	}

	private void testGenericType(Class<?>... input) {
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

	private void testSimpleTypes(Predicate<String> namePredicate, FieldFilter accessorTypeFilter,
			Function<Field, FieldSpec> fieldToSpec, Class<?>... differentTypes) {
		Field[] fields = Arrays.stream(differentTypes).map(c -> new Field(c)).toArray(Field[]::new);
		testTypes(namePredicate, accessorTypeFilter, fieldToSpec, fields);
	}

	private void testParameterizedTypes(Predicate<String> namePredicate,
			FieldFilter accessorTypeFilter, Function<Field, FieldSpec> fieldToSpec,
			Class<?> rawType, Class<?>... firstArgumentTypes) {
		for (Class<?> types : firstArgumentTypes) {
			testTypes(namePredicate, accessorTypeFilter, fieldToSpec, new Field(rawType, types));
		}
	}

	private void testTypes(Predicate<String> namePredicate, FieldFilter accessorTypeFilter,
			Function<Field, FieldSpec> fieldToSpec, Field... fields) {
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
	}

	public static class TestEnvironment {

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

			result = CompilerUtils.compile(Thread.currentThread().getContextClassLoader(),
					base.processors(), sources.stream().map(JavaSource::generateFileObject)
							.toArray(JavaFileObject[]::new));
			final Class<?> testClass;
			try {
				final String fqcn = sources.get(0).fqcn();
				testClass = result.classLoader.loadClass(fqcn);
				final Class<?> generatedClass = result.classLoader
						.loadClass(Akatsuki.generateRetainerClassName(fqcn));
				retainer = newInstance(generatedClass);
				mockedSource = mock(testClass);
				mockedBundle = mock(Bundle.class);
			} catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
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
			return result.printAllSources();
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
