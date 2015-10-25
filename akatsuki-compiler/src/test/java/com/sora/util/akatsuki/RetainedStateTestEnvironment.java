package com.sora.util.akatsuki;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.mockito.ArgumentCaptor;

import android.os.Bundle;

import com.google.common.collect.Sets;

public class RetainedStateTestEnvironment extends BaseTestEnvironment {

	public enum Accessor {
		PUT, GET
	}

	public interface FieldFilter {
		boolean test(TestField field, Class<?> type, Type[] arguments);
	}

	private BundleRetainer<Object> retainer; // no <?> because mokito blows up

	private BundleRetainerTester tester;

	public RetainedStateTestEnvironment(IntegrationTestBase base, List<TestSource> sources) {
		super(base, sources);
	}

	public RetainedStateTestEnvironment(IntegrationTestBase base, TestSource source, TestSource... required) {
		super(base, source, required);
	}

	@Override
	protected void setupTestEnvironment() throws Exception {
		final Class<?> testClass;
		// the first class is our test class
		final String fqcn = sources.get(0).fqcn();
		System.out.println("Loading class " + fqcn + " as test class");
		testClass = classLoader().loadClass(fqcn);

		RetainerCache retainerCache = null;
		try {
			final Class<?> retainerCacheClass = classLoader().loadClass(
					Akatsuki.RETAINER_CACHE_PACKAGE + "." + Akatsuki.RETAINER_CACHE_NAME);
			retainerCache = (RetainerCache) retainerCacheClass.newInstance();
		} catch (Exception ignored) {
			// doesn't really matter
			// throw new AssertionError(ignored);
		}
		retainer = Internal.createRetainer(classLoader(), retainerCache, testClass, Retained.class);
		tester = new BundleRetainerTester(this, testClass.newInstance(), mock(Bundle.class),
				retainer);
	}

	public BundleRetainerTester tester() {
		return tester;
	}

	public static class BundleRetainerTester {

		private final TestEnvironment environment;
		private final Object mockedSource;
		private final Bundle mockedBundle;
		private final BundleRetainer<Object> retainer;

		BundleRetainerTester(TestEnvironment environment, Object mockedSource, Bundle mockedBundle,
				BundleRetainer<Object> retainer) {

			this.environment = environment;
			this.mockedSource = mockedSource;
			this.mockedBundle = mockedBundle;
			this.retainer = retainer;
		}

		public void invokeSave() {
			try {
				retainer.save(mockedSource, mockedBundle);
			} catch (Exception e) {
				throw new AssertionError("Unable to invoke save. " + environment.printReport(), e);
			}
		}

		public void invokeRestore() {
			try {
				retainer.restore(mockedSource, mockedBundle);
			} catch (Exception e) {
				throw new AssertionError("Unable to invoke restore. " + environment.printReport(),
						e);
			}
		}

		public void invokeSaveAndRestore() {
			invokeSave();
			invokeRestore();
		}

		public void testSaveRestoreInvocation(Predicate<String> namePredicate,
				FieldFilter accessorTypeFilter, Set<RetainedTestField> fields,
				Function<TestField, Integer> times) {
			for (Accessor accessor : Accessor.values()) {
				executeTestCaseWithFields(fields, namePredicate, accessorTypeFilter, accessor,
						times);
			}
		}

		public void testSaveRestoreInvocation(Predicate<String> namePredicate,
				FieldFilter accessorTypeFilter, List<? extends RetainedTestField> fields,
				Function<TestField, Integer> times) {
			final HashSet<RetainedTestField> set = Sets.newHashSet(fields);
			if (set.size() != fields.size())
				throw new IllegalArgumentException("Duplicate fields are not allowed");
			testSaveRestoreInvocation(namePredicate, accessorTypeFilter, set, times);
		}

		public static FieldFilter ALWAYS = (f, t, a) -> true;
		public static FieldFilter NEVER = (f, t, a) -> false;
		public static FieldFilter CLASS_EQ = (f, t, a) -> f.clazz.equals(t);
		public static FieldFilter ASSIGNABLE = (f, t, a) -> t.isAssignableFrom(f.clazz);

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

		public AccessorKeyPair captureTestCaseKeysWithField(RetainedTestField field,
				Predicate<String> methodNamePredicate, FieldFilter accessorTypeFilter) {
			return new AccessorKeyPair(
					captureTestCaseKeyWithField(field, methodNamePredicate, accessorTypeFilter,
							Accessor.PUT),
					captureTestCaseKeyWithField(field, methodNamePredicate, accessorTypeFilter,
							Accessor.GET));
		}

		public String captureTestCaseKeyWithField(RetainedTestField field,
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
							+ " on mocked object " + "failed." + environment.printReport(), e);
				}
			}
			throw new RuntimeException("No invocation caught for field: " + field.toString()
					+ environment.printReport());
		}

		public void executeTestCaseWithFields(Set<? extends TestField> fieldList,
				Predicate<String> methodNamePredicate, FieldFilter accessorTypeFilter,
				Accessor accessor, Function<TestField, Integer> times) {
			Set<TestField> allFields = new HashSet<>(fieldList);

			for (Method method : Bundle.class.getMethods()) {

				// check correct signature and name predicate
				if (!checkMethodIsAccessor(method, accessor)
						|| !methodNamePredicate.test(method.getName())) {
					continue;
				}

				// find methods who's accessor type matches the given fields
				List<TestField> matchingField = allFields.stream()
						.filter(f -> filterTypes(method, accessor, accessorTypeFilter, f))
						.collect(Collectors.toList());

				// no signature match
				if (matchingField.isEmpty()) {
					continue;
				}

				// more than one match, we should have exactly one match
				if (matchingField.size() > 1) {
					throw new AssertionError(method.toString() + " matches multiple field "
							+ fieldList + ", this is ambiguous and should not happen."
							+ environment.printReport());
				}
				final TestField field = matchingField.get(0);
				try {
					if (accessor == Accessor.PUT) {
						method.invoke(verify(mockedBundle, times(times.apply(field))),
								eq(field.name), any(field.clazz));
					} else {
						method.invoke(verify(mockedBundle, times(times.apply(field))),
								eq(field.name));
					}
					allFields.remove(field);

				} catch (Exception e) {
					throw new AssertionError("Invocation of method " + method.getName()
							+ " on mocked object failed." + environment.printReport(), e);
				}
			}
			if (!allFields.isEmpty())
				throw new RuntimeException("While testing for accessor:" + accessor
						+ " some fields are left untested because a suitable accessor cannot be found: "
						+ allFields + environment.printReport());
		}

		private boolean filterTypes(Method method, Accessor accessor,
				FieldFilter accessorTypeFilter, TestField field) {
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
