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

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Primitives;
import com.sora.util.akatsuki.Akatsuki;
import com.sora.util.akatsuki.BundleRetainer;
import com.sora.util.akatsuki.Retained;
import com.sora.util.akatsuki.compiler.CodeGenUtils.JavaSource;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.AdditionalMatchers;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
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

	@Before
	public void incrementIdentifier() {
		classIdentifier.incrementAndGet();
	}

	@Test(expected = RuntimeException.class)
	public void testUnsupportedType() throws Exception {

		final JavaSource source = new JavaSource(TEST_PACKAGE,
				TEST_CLASS + classIdentifier.incrementAndGet(), Modifier.PUBLIC)
						.fields(field(ClassName.get(Activity.class), "badType", Retained.class));
		new MockedClasses(this, source);
	}

	@Test
	public void testPrimitives() throws Exception {
		testSimpleTypes(t -> true, CLASS, null, PRIMITIVES);
	}

	// TODO we got some wicked problem with boxed types, the compiled retainer
	// throws NPE at lines that doesn't even contain code
	@Ignore
	@Test
	public void testBoxedPrimitives() throws Exception {
		final Class<?>[] classes = Arrays.stream(PRIMITIVES).map(Primitives::wrap)
				.toArray(Class<?>[]::new);
		// boxed primitives cannot be null otherwise we get NPE when unboxing
		ImmutableMap<Class<?>, String> defaultValueMap = ImmutableMap.<Class<?>, String> builder()
				.put(Byte.class, "0").put(Short.class, "0").put(Integer.class, "0")
				.put(Long.class, "0L").put(Float.class, "0.0F").put(Double.class, "0.0D")
				.put(Character.class, "\'a\'").put(Boolean.class, "false").build();
		testSimpleTypes(t -> true, CLASS, f -> field(f.typeName(), f.name, Retained.class,
				defaultValueMap.getOrDefault(f.clazz, null)), classes);

	}

	@Test
	public void testPrimitiveArrays() throws Exception {
		final Class<?>[] classes = Arrays.stream(PRIMITIVES).map(this::toArrayClass)
				.toArray(Class<?>[]::new);
		testSimpleTypes(n -> n.contains("Array"), CLASS, null, classes);
	}

	@Test
	public void testSupportedSimpleTypes() throws Exception {
		testSimpleTypes(n -> true, CLASS, null, SUPPORTED_SIMPLE_CLASSES);
	}

	@Test
	public void testSubclassOfSupportedTypes() throws Exception {
		for (Entry<Class<?>, Class<?>> entry : SUPPORTED_SIMPLE_SUBCLASSES_MAP.entrySet()) {
			testSimpleTypes(n -> true, (f, t, a) -> t.equals(entry.getValue()), null,
					entry.getKey());
		}
	}

	@Test
	public void testParcelableAndParcelableSubclassTypes() throws Exception {
		// parcelable requires special care because the get accessor returns a
		// <T> instead of Parcelable
		for (Class<? extends Parcelable> type : PARCELABLES_CLASSES) {
			// filter out the method [get|set]Parcelable and work with that only
			testSimpleTypes(n -> n.endsWith("Parcelable"), ALWAYS, null, type);
		}
	}

	@Test
	public void testSupportedArrayTypes() throws Exception {
		testSimpleTypes(n -> n.contains("Array"), CLASS, null, SUPPORTED_ARRAY_CLASSES);
	}

	@Test
	public void testSupportedArraySubclassTypes() throws Exception {
		List<Class<?>> classes = new ArrayList<>();
		classes.addAll(Arrays.asList(PARCELABLES_CLASSES));
		classes.addAll(Arrays.asList(StringBuilder.class, CharBuffer.class, Spannable.class,
				Editable.class, Spanned.class));

		for (Class<?> type : classes) {
			// the type of the accessor must be assignable to the field's
			testSimpleTypes(n -> n.contains("Array"), ASSIGNABLE, null, toArrayClass(type));
		}
	}

	@Test
	public void testSparseArrayParcelableType() throws Exception {
		testParameterizedTypes(n -> n.contains("SparseParcelableArray"), CLASS, null,
				SparseArray.class, PARCELABLES_CLASSES);
	}

	@Test
	public void testParcelableArrayListType() throws Exception {
		// parcelable arraylist also requires special care because the generic
		// argument of the setter is a wildcard (<? extends Parcelable>)
		testParameterizedTypes(n -> n.contains("ParcelableArrayList"), ALWAYS, null,
				ArrayList.class, PARCELABLES_CLASSES);

	}

	@Test
	public void testSupportedArrayListTypes() throws Exception {
		testParameterizedTypes(n -> n.contains("ArrayList"),
				(f, t, a) -> t.equals(ArrayList.class) && Arrays.equals(a, f.parameters), null,
				ArrayList.class, String.class, Integer.class, CharSequence.class);

	}

	@Test
	public void testSimpleInheritance()
			throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		final JavaSource superClass = new JavaSource(TEST_PACKAGE,
				TEST_CLASS + classIdentifier.incrementAndGet(), Modifier.PUBLIC)
						.fields(field(STRING_TYPE, "a", Retained.class));
		final JavaSource subclass = new JavaSource(TEST_PACKAGE,
				TEST_CLASS + "Client" + classIdentifier.incrementAndGet(), Modifier.PUBLIC)
						.fields(field(STRING_TYPE, "b", Retained.class)).superClass(superClass);

		final MockedClasses classes = new MockedClasses(this, subclass, superClass);
		classes.invokeSaveAndRestore();
		verify(classes.mockedBundle, times(2)).putString(AdditionalMatchers.or(eq("a"), eq("b")),
				any());
		verify(classes.mockedBundle, times(2)).getString(AdditionalMatchers.or(eq("a"), eq("b")));
	}

	@Test
	public void testMultiLevelInheritance()
			throws InstantiationException, IllegalAccessException, ClassNotFoundException {

		final JavaSource superClass = new JavaSource(TEST_PACKAGE,
				TEST_CLASS + classIdentifier.incrementAndGet(), Modifier.PUBLIC)
						.fields(field(STRING_TYPE, "a", Retained.class));
		final JavaSource subclass1 = new JavaSource(TEST_PACKAGE,
				TEST_CLASS + "Client" + classIdentifier.incrementAndGet(), Modifier.PUBLIC)
						.fields(field(STRING_TYPE, "b", Retained.class)).superClass(superClass);

		final JavaSource subclass2 = new JavaSource(TEST_PACKAGE,
				TEST_CLASS + "Client" + classIdentifier.incrementAndGet(), Modifier.PUBLIC)
						.fields(field(STRING_TYPE, "c", Retained.class)).superClass(subclass1);

		final MockedClasses classes = new MockedClasses(this, subclass2, subclass1, superClass);
		classes.invokeSaveAndRestore();

		final ArgumentCaptor<String> putCaptor = ArgumentCaptor.forClass(String.class);
		final ArgumentCaptor<String> getCaptor = ArgumentCaptor.forClass(String.class);
		verify(classes.mockedBundle, times(3)).putString(putCaptor.capture(), any());
		verify(classes.mockedBundle, times(3)).getString(getCaptor.capture());

		final List<String> names = Arrays.asList("a", "b", "c");

		Assert.assertEquals("Some fields were not saved, ", names, putCaptor.getAllValues());
		Assert.assertEquals("Some fields were not saved, ", names, getCaptor.getAllValues());
	}

	@Test
	public void testSameTypeFieldHiding()
			throws InstantiationException, IllegalAccessException, ClassNotFoundException {

		final MockedClasses classes = createClassesWithFieldHiding(String.class, String.class);

		ArgumentCaptor<String> putCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> getCaptor = ArgumentCaptor.forClass(String.class);
		verify(classes.mockedBundle, times(2)).putString(putCaptor.capture(), anyString());
		verify(classes.mockedBundle, times(2)).getString(getCaptor.capture());
		final List<String> putArgs = putCaptor.getAllValues();
		final List<String> getArgs = getCaptor.getAllValues();
		Assert.assertTrue("Field with the same name is saved with the same name causing "
				+ "overwrites:" + putArgs, new HashSet<>(putArgs).size() == putArgs.size());
		Assert.assertTrue("Field with the same name is saved with the same name causing "
				+ "overwrites:" + getArgs, new HashSet<>(getArgs).size() == getArgs.size());
	}

	@Test
	public void testDifferentTypeFieldHiding()
			throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		final MockedClasses classes = createClassesWithFieldHiding(String.class, int.class);

		ArgumentCaptor<String> putCaptorString = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> getCaptorString = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> putCaptorInt = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> getCaptorInt = ArgumentCaptor.forClass(String.class);

		verify(classes.mockedBundle, times(1)).putString(putCaptorString.capture(), anyString());
		verify(classes.mockedBundle, times(1)).putInt(putCaptorInt.capture(), anyInt());
		verify(classes.mockedBundle, times(1)).getString(getCaptorString.capture());
		verify(classes.mockedBundle, times(1)).getInt(getCaptorInt.capture());

		Assert.assertEquals(putCaptorString.getValue(), getCaptorString.getValue());
		Assert.assertEquals(putCaptorInt.getValue(), getCaptorInt.getValue());

		Assert.assertNotEquals(
				"Field with the same name is saved with the same name causing overwrites:",
				putCaptorString.getValue(), putCaptorInt.getValue());
		Assert.assertNotEquals(
				"Field with the same name is saved with the same name causing overwrites:",
				getCaptorString.getValue(), getCaptorInt.getValue());

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

	enum Accessor {
		PUT, GET
	}

	interface MethodVerifier {
		void tryVerify(Method method, Accessor accessor, Type type) throws Exception;
	}

	private <A, B> MockedClasses createClassesWithFieldHiding(Class<A> first, Class<B> second)
			throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		final JavaSource superClass = new JavaSource(TEST_PACKAGE,
				TEST_CLASS + classIdentifier.incrementAndGet(), Modifier.PUBLIC)
						.fields(field(TypeName.get(first), "a", Retained.class));
		final JavaSource subclass = new JavaSource(TEST_PACKAGE,
				TEST_CLASS + "Client" + classIdentifier.incrementAndGet(), Modifier.PUBLIC)
						.fields(field(TypeName.get(second), "a", Retained.class))
						.superClass(superClass);

		MockedClasses classes = new MockedClasses(this, subclass, superClass);
		classes.invokeSaveAndRestore();
		return classes;
	}

	private void testGenericType(Class<?>... input)
			throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		final TypeVariableName typeVariable = TypeVariableName.get("T", input);
		final JavaSource source = new JavaSource(TEST_PACKAGE,
				TEST_CLASS + classIdentifier.incrementAndGet(), Modifier.PUBLIC)
						.fields(field(TypeVariableName.get("T"), "t", Retained.class))
						.builderTransformer((b, s) -> b.addTypeVariable(typeVariable));

		final MockedClasses classes = new MockedClasses(this, source);
		classes.invokeSaveAndRestore();
		verify(classes.mockedBundle, times(1)).putParcelable(eq("t"), any());
		verify(classes.mockedBundle, times(1)).getParcelable(eq("t"));
	}

	private void testSimpleTypes(Predicate<String> namePredicate, FieldFilter accessorTypeFilter,
			Function<Field, FieldSpec> fieldToSpec, Class<?>... differentTypes) throws Exception {
		Field[] fields = Arrays.stream(differentTypes).map(c -> new Field(c)).toArray(Field[]::new);
		testTypes(namePredicate, accessorTypeFilter, fieldToSpec, fields);

	}

	private void testParameterizedTypes(Predicate<String> namePredicate,
			FieldFilter accessorTypeFilter, Function<Field, FieldSpec> fieldToSpec,
			Class<?> rawType, Class<?>... firstArgumentTypes) throws Exception {
		for (Class<?> types : firstArgumentTypes) {
			testTypes(namePredicate, accessorTypeFilter, fieldToSpec, new Field(rawType, types));
		}
	}

	private void testTypes(Predicate<String> namePredicate, FieldFilter accessorTypeFilter,
			Function<Field, FieldSpec> fieldToSpec, Field... fields) throws Exception {

		List<Field> fieldList = new ArrayList<>(Arrays.asList(fields));

		// mockito explodes if the classes are not public...
		final JavaSource source = new JavaSource("test", "Test" + classIdentifier.incrementAndGet(),
				Modifier.PUBLIC)
						.fields(fieldList.stream()
								.map(f -> fieldToSpec != null ? fieldToSpec.apply(f)
										: field(f.typeName(), f.name, Retained.class))
								.collect(Collectors.toList()));

		// System.out.println(source.generateSource());
		final MockedClasses classes = new MockedClasses(this, source);
		classes.invokeSaveAndRestore();
		for (Accessor accessor : Accessor.values())
			executeReflectedMethodInvocation(classes, fieldList, namePredicate, accessorTypeFilter,
					accessor);
	}

	private interface FieldFilter {
		boolean test(Field field, Class<?> type, Type[] arguments);
	}

	static FieldFilter ALWAYS = (f, t, a) -> true;
	static FieldFilter CLASS = (f, t, a) -> f.clazz.equals(t);
	static FieldFilter ASSIGNABLE = (f, t, a) -> t.isAssignableFrom(f.clazz);

	private void executeReflectedMethodInvocation(MockedClasses classes, List<Field> fieldList,
			Predicate<String> namePredicate, FieldFilter accessorTypeFilter, Accessor accessor) {
		List<Field> allFields = new ArrayList<>(fieldList);
		for (Method method : Bundle.class.getMethods()) {
			// wrong signature
			if (!checkMethodIsAccessor(method, accessor, namePredicate))
				continue;

			// find methods who's accessor type matches the given fields
			List<Field> matchingField = allFields.stream().filter(f -> {
				Parameter[] parameters = method.getParameters();
				Class<?> type = accessor == Accessor.PUT ? parameters[1].getType()
						: method.getReturnType();
				Type[] arguments = {};

				final Type genericType = accessor == Accessor.PUT
						? parameters[1].getParameterizedType() : method.getGenericReturnType();
				if (genericType instanceof ParameterizedType) {
					if (!f.generic()) {
						return false;
					}
					arguments = ((ParameterizedType) genericType).getActualTypeArguments();
				}

				return accessorTypeFilter.test(f, type, arguments);

			}).collect(Collectors.toList());

			// no signature match
			if (matchingField.isEmpty())
				continue;

			// more than one match, we should have exactly one match
			if (matchingField.size() > 1) {
				throw new AssertionError(method.toString() + " matches multiple field " + fieldList
						+ ", this is ambiguous and should not happen");
			}

			final Field field = matchingField.get(0);

			try {
				if (accessor == Accessor.PUT) {
					method.invoke(verify(classes.mockedBundle, times(1)), eq(field.name),
							any(field.clazz));
				} else {
					method.invoke(verify(classes.mockedBundle, times(1)), eq(field.name));
				}

				allFields.remove(field);

			} catch (Exception e) {
				throw new RuntimeException("invocation of method " + method.getName()
						+ " on mocked object " + "failed", e);
			}
		}
		if (!allFields.isEmpty())
			throw new RuntimeException("while testing for accessor:" + accessor
					+ " some fields are untested :" + allFields);
	}

	private boolean checkMethodIsAccessor(Method m, Accessor accessor,
			Predicate<String> namePredicate) {
		final String name = m.getName();
		boolean correctSignature = name.startsWith(accessor.name().toLowerCase())
				&& name.length() > 3 && namePredicate.test(name)
				&& m.getParameterCount() == (accessor == Accessor.PUT ? 2 : 1);
		if (!correctSignature)
			return false;
		final Parameter[] parameters = m.getParameters();
		return parameters[0].getType().equals(String.class);
	}

	public static class MockedClasses {

		final Object mockedSource;
		final Bundle mockedBundle;
		final BundleRetainer<Object> retainer;

		public MockedClasses(TestBase base, JavaSource source, JavaSource... required)
				throws ClassNotFoundException, InstantiationException, IllegalAccessException {

			ArrayList<JavaSource> sources = new ArrayList<>(Arrays.asList(required));
			sources.add(source);

			final ClassLoader loader = CompilerUtils.compile(
					Thread.currentThread().getContextClassLoader(), base.processors(),
					sources.stream().map(JavaSource::generateFileObject)
							.toArray(JavaFileObject[]::new));

			final Class<?> testClass = loader.loadClass(source.fqcn());
			final Class<?> generatedClass = loader
					.loadClass(Akatsuki.generateRetainerClassName(source.fqcn()));
			retainer = newInstance(generatedClass);
			mockedSource = mock(testClass);
			mockedBundle = mock(Bundle.class);
		}

		public void invokeSaveAndRestore() {
			retainer.save(mockedSource, mockedBundle);
			retainer.restore(mockedSource, mockedBundle);
		}

	}

	public static final class Field {
		public final Class<?> clazz;
		public final Class<?>[] parameters;
		public final String name;

		public Field(Class<?> clazz, Class<?>... parameters) {
			this.clazz = clazz;
			this.parameters = parameters;
			this.name = "_" + createName(clazz, parameters);
		}

		private static String createName(Class<?> clazz, Class<?>... parameters) {
			if (clazz.isArray()) {
				return createName(clazz.getComponentType(), parameters) + "Array";
			} else {
				return parameters.length == 0 ? clazz.getSimpleName()
						: Arrays.stream(parameters).map(p -> createName(p))
								.collect(Collectors.joining()) + createName(clazz);
			}
		}

		public boolean generic() {
			return parameters.length > 0;
		}

		public TypeName typeName() {
			return generic() ? ParameterizedTypeName.get(clazz, parameters) : TypeName.get(clazz);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			Field field = (Field) o;
			return Objects.equal(clazz, field.clazz) && Objects.equal(parameters, field.parameters)
					&& Objects.equal(name, field.name);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(clazz, parameters, name);
		}

		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this).add("clazz", clazz)
					.add("parameters", parameters).add("name", name).toString();
		}
	}

}
