package com.sora.util.akatsuki;

import java.io.Serializable;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;

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

public class SupportedTypeTest extends RetainedStateTestBase {

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
		final JavaSource source = new JavaSource(TEST_PACKAGE, generateClassName(), Modifier.PUBLIC)
				.fields(field(ClassName.get(Activity.class), "badType", Retained.class));
		new RetainedStateTestEnvironment(this, source);
	}

	@Test
	public void testPrimitives() {
		testSimpleTypes(t -> true, BundleRetainerTester.CLASS, null, PRIMITIVES);
	}

	// TODO we got some wicked problem with boxed types, the compiled retainer
	// throws NPE at lines that doesn't even contain code
	@Test
	public void testBoxedPrimitives() {
		final Class<?>[] classes = Arrays.stream(PRIMITIVES).map(Primitives::wrap)
				.toArray(Class<?>[]::new);
		// boxed primitives cannot be null otherwise we get NPE when unboxing
		ImmutableMap<Class<?>, String> defaultValueMap = ImmutableMap.<Class<?>, String> builder()
				.put(Byte.class, "0").put(Short.class, "0").put(Integer.class, "0")
				.put(Long.class, "0L").put(Float.class, "0.0F").put(Double.class, "0.0D")
				.put(Character.class, "\'a\'").put(Boolean.class, "false").build();
		testSimpleTypes(t -> true, BundleRetainerTester.CLASS, f -> field(f.typeName(), f.name,
				Retained.class, defaultValueMap.getOrDefault(f.clazz, null)), classes);

	}

	@Test
	public void testPrimitiveArrays() {
		final Class<?>[] classes = Arrays.stream(PRIMITIVES).map(this::toArrayClass)
				.toArray(Class<?>[]::new);
		testSimpleTypes(n -> n.contains("Array"), BundleRetainerTester.CLASS, null, classes);
	}

	@Test
	public void testSupportedSimpleTypes() {
		testSimpleTypes(n -> true, BundleRetainerTester.CLASS, null, SUPPORTED_SIMPLE_CLASSES);
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
			testSimpleTypes(n -> n.endsWith("Parcelable"), BundleRetainerTester.ALWAYS, null, type);
		}
	}

	@Test
	public void testSupportedArrayTypes() {
		testSimpleTypes(n -> n.contains("Array"), BundleRetainerTester.CLASS, null,
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
			testSimpleTypes(n -> n.contains("Array"), BundleRetainerTester.ASSIGNABLE, null,
					toArrayClass(type));
		}
	}

	@Test
	public void testSparseArrayParcelableType() {
		testParameterizedTypes(n -> n.contains("SparseParcelableArray"), BundleRetainerTester.CLASS,
				null, SparseArray.class, PARCELABLES_CLASSES);
	}

	@Test
	public void testParcelableArrayListType() {
		// parcelable arraylist also requires special care because the generic
		// argument of the setter is a wildcard (<? extends Parcelable>)
		testParameterizedTypes(n -> n.contains("ParcelableArrayList"), BundleRetainerTester.ALWAYS,
				null, ArrayList.class, PARCELABLES_CLASSES);

	}

	@Test
	public void testSupportedArrayListTypes() {
		testParameterizedTypes(n -> n.contains("ArrayList"),
				(f, t, a) -> t.equals(ArrayList.class) && Arrays.equals(a, f.parameters), null,
				ArrayList.class, String.class, Integer.class, CharSequence.class);

	}

	@Test
	public void testSupportedCollectionTypes() {
		testParameterizedTypes(n -> n.contains("ArrayList"),
				(f, t, a) -> t.equals(ArrayList.class) && Arrays.equals(a, f.parameters), null,
				ArrayList.class, String.class, Integer.class, CharSequence.class);

	}

	@Test
	public void testSimpleInheritance1() {
		testInheritance(true, new RetainedField(String.class, "a"),
				new RetainedField(int.class, "b"));
	}

	@Test
	public void testSimpleInheritance2() {
		testInheritance(true, new RetainedField(CharSequence.class, "a"),
				new RetainedField(Parcelable.class, "b"));
	}

	@Test
	public void testSimpleInheritance3() {
		testInheritance(true, new RetainedField(float.class, "a"),
				new RetainedField(double.class, "b"));
	}

	@Test
	public void testMultiLevelInheritance1() {
		testInheritance(true, new RetainedField(String.class, "a"),
				new RetainedField(int.class, "b"), new RetainedField(long.class, "c"));
	}

	@Test
	public void testDeepInheritance() {
		RetainedField[] fields = new RetainedField[SUPPORTED_SIMPLE_CLASSES.length];
		for (int i = 0; i < fields.length; i++) {
			fields[i] = new RetainedField(SUPPORTED_SIMPLE_CLASSES[i], "s" + i);
		}
		testInheritance(true, new RetainedField(int.class, "first"), fields);
	}

	@Test
	public void testMultiLevelInheritance2() {
		testInheritance(true, new RetainedField(int.class, "d"),
				new RetainedField(String.class, "e"), new RetainedField(long.class, "f"),
				new RetainedField(Parcelable.class, "g"));
	}

	@Test
	public void testMultiLevelInheritanceWithGap() {
		testInheritance(true, new RetainedField(String.class, "h"), new Field(int.class, "i"),
				new RetainedField(long.class, "j"));
	}

	@Test
	public void testInheritanceWithoutAnnotations() {
		testInheritance(true, new Field(int.class, "a"), new RetainedField(String.class, "b"));
	}

	@Test
	public void testInheritanceWithoutAnnotationsAndCache() {
		testInheritance(false, new Field(int.class, "a"), new RetainedField(String.class, "b"));
	}

	@Test
	public void testFieldHiding() {
		final RetainedField first = new RetainedField(String.class, "a");
		final RetainedField second = new RetainedField(int.class, "a");
		final RetainedStateTestEnvironment environment = testFieldHiding(first, second);
		environment.tester().invokeSaveAndRestore();
		final AccessorKeyPair firstKeys = environment.tester().captureTestCaseKeysWithField(first,
				n -> true, BundleRetainerTester.CLASS);
		final AccessorKeyPair secondKeys = environment.tester().captureTestCaseKeysWithField(second,
				n -> true, BundleRetainerTester.CLASS);
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
