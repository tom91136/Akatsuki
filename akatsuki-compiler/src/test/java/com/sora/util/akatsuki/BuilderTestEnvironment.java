package com.sora.util.akatsuki;

import android.os.Bundle;

import com.sora.util.akatsuki.Internal.ArgBuilder;
import com.sora.util.akatsuki.RetainedStateTestEnvironment.BundleRetainerTester;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class BuilderTestEnvironment extends BaseTestEnvironment {
	public BuilderTestEnvironment(TestBase base, List<JavaSource> sources) {
		super(base, sources);
	}

	public BuilderTestEnvironment(TestBase base, JavaSource source, JavaSource... required) {
		super(base, source, required);
	}

	@Override
	protected void setupTestEnvironment() throws Exception {
		// nah
	}

	static class SingleBuilderTester {

		// these classes form a valid builder
		private final Class<?> builderParentClass;
		private final Class<?> builderClass;
		private final Class<?> retainerClass;

		private final BuilderTestEnvironment environment;
		private final JavaSource source;

		private final Method builderClassMethodWithBundle;
		protected ArgBuilder<?> builderInstance;

		protected BundleRetainer<Object> builderBundleRetainer;
		private Bundle mockedBundle;

		public SingleBuilderTester(BuilderTestEnvironment environment, JavaSource source) {
			this.environment = environment;
			this.source = source;

			// verify builder structure
			try {

				String builderParentName = source.packageName + "."
						+ ArgumentBuilderModel.BUILDER_CLASS_NAME;
				builderParentClass = environment.classLoader().loadClass(builderParentName);
				String builderClassName = source.className
						+ ArgumentBuilderModel.BUILDER_CLASS_SUFFIX;
				// <package>.<builderName>
				builderClass = environment.classLoader()
						.loadClass(builderParentName + "$" + builderClassName);
				// <package>.<builderName>.<retainerName>
				retainerClass = environment.classLoader()
						.loadClass(Internal.generateRetainerClassName(builderParentName + "$"
								+ builderClassName + "$" + source.className));

				try {
					builderClassMethodWithBundle = builderParentClass.getMethod(source.className,
							Bundle.class);
					Method builderClassMethod = builderParentClass.getMethod(source.className);

					assertEquals("Different return types from builder method, should not happen",
							builderClassMethod.getReturnType(),
							builderClassMethodWithBundle.getReturnType());

					assertEquals("Builder methods should not have any parameters",
							builderClassMethod.getParameterCount(), 0);

					assertTrue("Builder methods should be public",
							Modifier.isPublic(builderClassMethod.getModifiers()) && Modifier
									.isPublic(builderClassMethodWithBundle.getModifiers()));

					Class<?> builderMethodReturnType = builderClassMethod.getReturnType();
					// builderClassMethod and builderClassMethodWithBundle
					// should have same return type at this point
					assertEquals("Builder class should equal method return class", builderClass,
							builderMethodReturnType);
				} catch (NoSuchMethodException e) {
					throw new AssertionError("Builder accessing method not found", e);
				}
			} catch (ClassNotFoundException e) {
				throw new AssertionError("Class expected but missing", e);
			}
		}

		public void initializeAndValidate() {
			Object builderParentInstance;
			try {
				builderParentInstance = builderParentClass.newInstance();
			} catch (InstantiationException | IllegalAccessException e) {
				throw new AssertionError("Unable to instantiate Builders class for package "
						+ source.packageName + " for source class " + source.toString(), e);
			}
			try {
				mockedBundle = mock(Bundle.class);
				builderInstance = (ArgBuilder<?>) builderClassMethodWithBundle
						.invoke(builderParentInstance, mockedBundle);
				initializeRetainer();
			} catch (ClassCastException e) {
				throw new AssertionError("The returned builder class does not extend ArgBuilder",
						e);
			} catch (IllegalAccessException | InvocationTargetException e) {
				throw new AssertionError("Unable to invoke builder method", e);
			}
		}

		@SuppressWarnings("unchecked")
		private void initializeRetainer() {
			try {
				builderBundleRetainer = (BundleRetainer<Object>) retainerClass.newInstance();
			} catch (ClassCastException e) {
				throw new AssertionError(
						"Retainer class " + retainerClass + " does not implement BundleRetainer",
						e);
			} catch (InstantiationException | IllegalAccessException e) {
				throw new AssertionError(
						"Unable to create new instance of retainer class " + retainerClass, e);
			}
		}

		public Bundle mockedBundle() {
			return mockedBundle;
		}

		public BundleRetainerTester retainerTester() {
			try {
				return new RetainedStateTestEnvironment.BundleRetainerTester(environment,
						mock(environment.findClass(source.fqcn())), mockedBundle,
						builderBundleRetainer);
			} catch (ClassNotFoundException e) {
				throw new AssertionError("Unable to find class for " + source, e);
			}

		}

		public void assertMethodCountMatched(int methodCount, Predicate<Method> methodPredicate) {
			filterAndCountMethod(methodCount, methodPredicate, methods -> {
				throw new AssertionError(
						"Predicate matched more or less methods than expected, required "
								+ methodCount + " but matched " + methods);
			});
		}

		public void filterAndCountMethod(int methodCount, Predicate<Method> methodPredicate,
				Consumer<Set<Method>> action) {
			Set<Method> methods = Arrays.stream(builderClass.getMethods()).filter(methodPredicate)
					.collect(Collectors.toSet());
			System.out.println("Matching " + methods + " for "+methodCount);
			if (methods.size() != methodCount) {
				action.accept(methods);
			}
		}
	}

	public List<SingleBuilderTester> assertAllBuildersGeneratedAndValid() {
		return sources.stream().map(s -> new SingleBuilderTester(this, s))
				.peek(SingleBuilderTester::initializeAndValidate).collect(Collectors.toList());
	}

}
