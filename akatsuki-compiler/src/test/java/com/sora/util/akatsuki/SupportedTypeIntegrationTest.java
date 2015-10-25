package com.sora.util.akatsuki;

import java.io.Serializable;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Function;

import javax.lang.model.element.Modifier;

import org.junit.Test;

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
import com.google.common.primitives.Primitives;
import com.sora.util.akatsuki.RetainedStateTestEnvironment.BundleRetainerTester;
import com.sora.util.akatsuki.RetainedStateTestEnvironment.BundleRetainerTester.AccessorKeyPair;
import com.squareup.javapoet.ClassName;

public class SupportedTypeIntegrationTest extends RetainedStateIntegrationTestBase {

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

	@Test(expected = RuntimeException.class)
	public void testUnsupportedType() {
		final TestSource source = new TestSource(TEST_PACKAGE, generateClassName(), Modifier.PUBLIC)
				.appendFields(field(ClassName.get(Activity.class), "badType", Retained.class));
		new RetainedStateTestEnvironment(this, source);
	}

	@Test
	public void testPrimitives() {
		testSimpleTypes(t -> true, BundleRetainerTester.CLASS_EQ, Function.identity(), PRIMITIVES);
	}

	@Test
	public void testBoxedPrimitives() {
		// boxed primitives cannot be null otherwise we get NPE when unboxing
		ImmutableMap<Class<?>, String> defaultValueMap = ImmutableMap.<Class<?>, String> builder()
				.put(Byte.class, "0").put(Short.class, "0").put(Integer.class, "0")
				.put(Long.class, "0L").put(Float.class, "0.0F").put(Double.class, "0.0D")
				.put(Character.class, "\'a\'").put(Boolean.class, "false").build();

		RetainedTestField[] fields = defaultValueMap.entrySet()
				.stream().map(ent -> new RetainedTestField(ent.getKey(),
						"_" + ent.getKey().getSimpleName(), ent.getValue()))
				.toArray(RetainedTestField[]::new);
		// comparison between primitives and boxed primitives does not work,
		// manually wrap it
		testTypes(ALWAYS, (field, type, arguments) -> type.isPrimitive()
				&& field.clazz == Primitives.wrap(type), f->1, fields);

	}

	@Test
	public void testPrimitiveArrays() {
		final Class<?>[] classes = Arrays.stream(PRIMITIVES).map(this::toArrayClass)
				.toArray(Class<?>[]::new);
		testSimpleTypes(n -> n.contains("Array"), BundleRetainerTester.CLASS_EQ, Function.identity(),
				classes);
	}

	@Test
	public void testSupportedSimpleTypes() {
		testSimpleTypes(n -> true, BundleRetainerTester.CLASS_EQ, Function.identity(),
				SUPPORTED_SIMPLE_CLASSES);
	}

	@Test
	public void testSubclassOfSupportedTypes() {
		for (Entry<Class<?>, Class<?>> entry : SUPPORTED_SIMPLE_SUBCLASSES_MAP.entrySet()) {
			testSimpleTypes(n -> true, (f, t, a) -> t.equals(entry.getValue()), Function.identity(),
					entry.getKey());
		}
	}

	@Test
	public void testParcelableAndParcelableSubclassTypes() {
		// parcelable requires special care because the get accessor returns a
		// <T> instead of Parcelable
		for (Class<? extends Parcelable> type : PARCELABLES_CLASSES) {
			// filter out the method [get|set]Parcelable and work with that only
			testSimpleTypes(n -> n.endsWith("Parcelable"), BundleRetainerTester.ALWAYS,
					Function.identity(), type);
		}
	}

	@Test
	public void testSupportedArrayTypes() {
		testSimpleTypes(n -> n.contains("Array"), BundleRetainerTester.CLASS_EQ, Function.identity(),
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
			testSimpleTypes(n -> n.contains("Array"), BundleRetainerTester.ASSIGNABLE,
					Function.identity(), toArrayClass(type));
		}
	}

	@Test
	public void testSparseArrayParcelableType() {
		testParameterizedTypes(n -> n.contains("SparseParcelableArray"), BundleRetainerTester.CLASS_EQ,
				SparseArray.class, PARCELABLES_CLASSES);
	}

	@Test
	public void testParcelableArrayListType() {
		// parcelable arraylist also requires special care because the generic
		// argument of the setter is a wildcard (<? extends Parcelable>)
		testParameterizedTypes(n -> n.contains("ParcelableArrayList"), BundleRetainerTester.ALWAYS,
				ArrayList.class, PARCELABLES_CLASSES);

	}

	@Test
	public void testSupportedArrayListTypes() {
		testParameterizedTypes(n -> n.contains("ArrayList"),
				(f, t, a) -> t.equals(ArrayList.class) && Arrays.equals(a, f.parameters),
				ArrayList.class, String.class, Integer.class, CharSequence.class);

	}

	@Test
	public void testSupportedCollectionTypes() {
		testParameterizedTypes(n -> n.contains("ArrayList"),
				(f, t, a) -> t.equals(ArrayList.class) && Arrays.equals(a, f.parameters),
				ArrayList.class, String.class, Integer.class, CharSequence.class);

	}

	@Test
	public void testSimpleInheritance1() {
		testInheritance(true, new RetainedTestField(String.class, "a"),
				new RetainedTestField(int.class, "b"));
	}

	@Test
	public void testSimpleInheritance2() {
		testInheritance(true, new RetainedTestField(CharSequence.class, "a"),
				new RetainedTestField(Parcelable.class, "b"));
	}

	@Test
	public void testSimpleInheritance3() {
		testInheritance(true, new RetainedTestField(float.class, "a"),
				new RetainedTestField(double.class, "b"));
	}

	@Test
	public void testMultiLevelInheritance1() {
		testInheritance(true, new RetainedTestField(String.class, "a"),
				new RetainedTestField(int.class, "b"), new RetainedTestField(long.class, "c"));
	}

	@Test
	public void testDeepInheritance() {
		RetainedTestField[] fields = new RetainedTestField[SUPPORTED_SIMPLE_CLASSES.length];
		for (int i = 0; i < fields.length; i++) {
			fields[i] = new RetainedTestField(SUPPORTED_SIMPLE_CLASSES[i], "s" + i);
		}
		testInheritance(true, new RetainedTestField(int.class, "first"), fields);
	}

	@Test
	public void testMultiLevelInheritance2() {
		testInheritance(true, new RetainedTestField(int.class, "d"),
				new RetainedTestField(String.class, "e"), new RetainedTestField(long.class, "f"),
				new RetainedTestField(Parcelable.class, "g"));
	}

	@Test
	public void testMultiLevelInheritanceWithGap() {
		testInheritance(true, new RetainedTestField(String.class, "h"),
				new TestField(int.class, "i"), new RetainedTestField(long.class, "j"));
	}

	@Test
	public void testInheritanceWithoutAnnotations() {
		testInheritance(true, new TestField(int.class, "a"),
				new RetainedTestField(String.class, "b"));
	}

	@Test
	public void testInheritanceWithoutAnnotationsAndCache() {
		testInheritance(false, new TestField(int.class, "a"),
				new RetainedTestField(String.class, "b"));
	}

	@Test
	public void testFieldHiding() {
		final RetainedTestField first = new RetainedTestField(String.class, "a");
		final RetainedTestField second = new RetainedTestField(int.class, "a");
		final RetainedStateTestEnvironment environment = testFieldHiding(first, second);
		environment.tester().invokeSaveAndRestore();
		final AccessorKeyPair firstKeys = environment.tester().captureTestCaseKeysWithField(first,
				n -> true, BundleRetainerTester.CLASS_EQ);
		final AccessorKeyPair secondKeys = environment.tester().captureTestCaseKeysWithField(second,
				n -> true, BundleRetainerTester.CLASS_EQ);
		firstKeys.assertSameKeyUsed();
		secondKeys.assertSameKeyUsed();
		firstKeys.assertNotTheSame(secondKeys);
	}

	@Test
	public void testGenericTypeOfT() {
		for (Class<?> clazz : SUPPORTED_SIMPLE_CLASSES) {
			testGenericType("T", clazz);
		}
	}

	@Test
	public void testGenericTypeOfSomethingLong() {
		for (Class<?> clazz : SUPPORTED_SIMPLE_CLASSES) {
			testGenericType("SomeVeryLongParameterNameThatMightBreak", clazz);
		}
	}

	@Test
	public void testIntersectionType() {
		testGenericType("T", Parcelable.class, Serializable.class);
	}

}
