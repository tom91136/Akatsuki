package com.sora.util.akatsuki;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.junit.Test;

import android.os.Parcelable;

import com.sora.util.akatsuki.RetainedStateTestEnvironment.BundleRetainerTester;

public class ExtendedTypeSupportIntegrationTest extends RetainedStateIntegrationTestBase {

	@Test
	public void testSupportedArrayTypes() {
		testSimpleTypes(ALWAYS, BundleRetainerTester.CLASS_EQ, Function.identity(), String.class);
	}

	// TODO not anymore, maybe in the future (the test is likely broken in the
	// first place)
	// @Test
	// public void testMultidimensionalArrayTypes() {
	// testSimpleTypes(ALWAYS, BundleRetainerTester.CLASS_EQ,
	// Function.identity(),
	// String[][][][][].class);
	// }

	// TODO not anymore, maybe in the future (the test is likely broken in the
	// first place)
	// @Test
	// public void testGenericArrayTypes() {
	// testTypes(ALWAYS, BundleRetainerTester.CLASS_EQ, f -> 1,
	// new RetainedTestField(ArrayList[].class, "b", String.class));
	// }

	@Test
	public void testListInterface() {
		testTypes(n -> n.contains("ArrayList"),
				(f, t, a) -> t.equals(ArrayList.class) && isAllAssignable(a, f.parameters), f -> 1,
				new RetainedTestField(List.class, "test", "new java.util.ArrayList<>()",
						Parcelable.class));

	}

	@Test
	public void testArrayListTypes() {
		for (Class<?> clazz : Arrays.asList(LinkedList.class, CopyOnWriteArrayList.class)) {
			testTypes(n -> n.contains("ArrayList"),
					(f, t, a) -> t.equals(ArrayList.class) && isAllAssignable(a, f.parameters),
					f -> 1, new RetainedTestField(clazz, "_" + clazz.getSimpleName(),
							"new " + clazz.getName() + "<>()", Parcelable.class));
		}
	}

	private static boolean isAllAssignable(Type[] lhs, Class<?>[] rhs) {
		if (lhs.length != rhs.length) {
			return false;
		}
		for (int i = 0; i < lhs.length; i++) {
			Type t = lhs[i];
			Class<?> c = rhs[i];
			// XXX this is just a quick way to check the first type variable,
			// bad
			if (t instanceof WildcardType) {
				t = ((WildcardType) t).getUpperBounds()[0];
			} else if (t instanceof TypeVariable) {
				t = ((TypeVariable) t).getBounds()[0];
			}
			if (c.isAssignableFrom((Class<?>) t))
				return true;
		}
		return false;
	}

}
