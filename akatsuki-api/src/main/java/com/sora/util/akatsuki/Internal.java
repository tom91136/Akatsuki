package com.sora.util.akatsuki;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

import android.os.Bundle;
import android.util.Log;

/**
 * This is not the class you are looking for (unless you want to create your own
 * instance of {@link BundleRetainer}) for testing
 */
@SuppressWarnings("unused")
class Internal {

	static final String BUILDER_CLASS_NAME = "Builders";
	static final String BUILDER_CLASS_SUFFIX = "Builder";

	private static String getPackageNameString(Class<?> clazz) {
		Package pkg = clazz.getPackage();
		String packageName = null;
		if (pkg != null) {
			return pkg.getName();
		} else {
			String fqcn = clazz.getName();
			int i = fqcn.lastIndexOf('.');
			if (i != -1) {
				return fqcn.substring(0, i);
			}
		}
		return null;
	}

	/**
	 * Finds the {@link BundleRetainer} class and instantiate it. You would not
	 * normally need this, this method does not do any caching and is designed
	 * to be used from classes that require access to a fresh
	 * {@link BundleRetainer} instance
	 *
	 * @param <T>
	 *            the type of the annotated instance
	 * @param clazz
	 *            the {@link Class} of the instance
	 * @param type
	 *            the annotation type for retainer lookup
	 * @return the {@link BundleRetainer}
	 */
	@SuppressWarnings("unchecked")
	static <T> BundleRetainer<T> createRetainer(ClassLoader loader, RetainerCache cache,
			Class<?> clazz, Class<? extends Annotation> type) {
		if (clazz == null)
			throw new NullPointerException("class == null");
		if (type == null)
			throw new NullPointerException("annotation type == null");

		if (type != Retained.class && type != Arg.class) {
			throw new AssertionError("Unable to create retainer for unknown class " + type);
		}

		Class<? extends BundleRetainer<?>> retainerClass = type == Retained.class
				? findRetainedRetainerClass(loader, cache, clazz)
				: findArgRetainerClass(loader, cache, clazz);

		try {
			return (BundleRetainer<T>) retainerClass.newInstance();
		} catch (ClassCastException e) {
			throw new AssertionError(
					clazz + "does not implement BundleRetainer or has the wrong generic "
							+ "parameter, this should not happen at all",
					e);
		} catch (InstantiationException | IllegalAccessException e) {
			throw new RuntimeException("Unable to access/instantiate retainer class", e);
		}
	}

	@SuppressWarnings("unchecked")
	static <T> Class<? extends BundleRetainer<?>> findRetainedRetainerClass(ClassLoader loader,
			RetainerCache cache, Class<?> clazz) {
		final BundleRetainer<T> instance;
		final String fqcn = clazz.getName();

		Class<? extends BundleRetainer<T>> retainerClass = null;
		// TODO we didn't cache Arg.class , implement it
		if (cache != null)
			retainerClass = cache.getCached(fqcn);

		if (retainerClass == null) {
			String className = generateRetainerClassName(fqcn);
			try {
				retainerClass = (Class<? extends BundleRetainer<T>>) Class.forName(className, true,
						loader);
			} catch (ClassNotFoundException ignored) {
				Log.i(Akatsuki.TAG, "Retainer class does not exist for " + clazz
						+ ", was looking for " + className + "; trying inheritance next");
				// can't find it, moving on
			}
		}

		// search the tree
		if (retainerClass == null)
			retainerClass = (Class<? extends BundleRetainer<T>>) findClass(loader, clazz);

		if (retainerClass == null) {
			throw new RuntimeException("Unable to find generated class for " + fqcn
					+ " while traversing the class hierarchy."
					+ "\nYou cannot call Akatsuki.save/restore with classes that does not have fields annotated with @Retained."
					+ "\nIf proguard is turned on, please add the respective rules for Akatsuki.");

		}
		return retainerClass;
	}

	@SuppressWarnings("unchecked")
	static <T> Class<? extends BundleRetainer<T>> findArgRetainerClass(ClassLoader loader,
			RetainerCache cache, Class<T> clazz) {

		final BundleRetainer<T> instance;
		final String fqcn = clazz.getName();

		Class<? extends BundleRetainer<T>> retainerClass;

		Package pkg = clazz.getPackage();
		String packageName = getPackageNameString(clazz);
		// give up trying, we're not getting the package name
		if (packageName == null) {
			throw new RuntimeException("unable to obtain a package name from class " + clazz);
		}

		String name = clazz.getName();

		// strip the package name from the fqcn
		name = name.substring(packageName.length() + 1, name.length());

		String builderClassName = name + "Builder";

		//Log.i(Akatsuki.TAG, "clz simple -> " + builderClassName);
		// if (!clazz.isMemberClass() || false) {
		// int indexOfDollar = builderClassName.lastIndexOf('$');
		// if (indexOfDollar != -1) {
		// builderClassName = builderClassName.substring(indexOfDollar + 1,
		// builderClassName.length());
		// }
		// }

		String className = generateRetainerClassName(packageName + "." + BUILDER_CLASS_NAME + "$"
				+ builderClassName + "$" + builderClassName);

		//Log.i(Akatsuki.TAG, "bcn -> " + builderClassName);

		try {
			retainerClass = (Class<? extends BundleRetainer<T>>) Class.forName(className, true,
					loader);
		} catch (ClassNotFoundException e) {
			throw new AssertionError("Builder's Retainer class does not exist for " + clazz
					+ ", was looking for " + className + "; this should not happen at all", e);

		}

		return retainerClass;
	}

	/**
	 * Traverse the class hierarchy to find correct BundleRetainer
	 */
	private static Class<?> findClass(ClassLoader loader, Class<?> clazz) {
		final String name = clazz.getName();
		if (name.startsWith("android.") || name.startsWith("java."))
			return null;
		String generatedClassName = generateRetainerClassName(name);
		try {
			if (Akatsuki.loggingLevel == AkatsukiConfig.LoggingLevel.VERBOSE)
				Log.i(Akatsuki.TAG, "traversing hierarchy to find retainer for class " + clazz);
			return Class.forName(generatedClassName, true, loader);
		} catch (ClassNotFoundException e) {
			return findClass(loader, clazz.getSuperclass());
		}
	}

	/**
	 * Create the name for the generated class. <b>This is not the method you
	 * are looking for</b>
	 *
	 * @param prefix
	 *            the class name
	 */
	static String generateRetainerClassName(CharSequence prefix) {
		return prefix + "$$" + BundleRetainer.class.getSimpleName();
	}

	public static abstract class ArgBuilder<T> {

		protected Bundle bundle;

		protected abstract Class<? super T> targetClass();

		protected Bundle bundle() {
			return bundle;
		}

		protected void check() {
			// for subclass to do some checking
		}

	}

	// TODO allow the use of final fields in @Arg and possibly @Retained?
	static void setFieldUnsafe(Object object, Class<?> clazz, String fieldName, Object value) {
		try {
			Field field = clazz.getField(fieldName);
			field.setAccessible(true);
			field.set(object, value);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			throw new RuntimeException("unable to set field [" + fieldName + "] in class " + clazz
					+ " on object " + object + " with value " + value, e);
		}
	}

	public static class ClassArgBuilder<T> extends ArgBuilder<T> {

		private final Class<? super T> targetClass;

		public ClassArgBuilder(Bundle bundle, Class<? super T> targetClass) {
			this.bundle = bundle;
			this.targetClass = targetClass;
		}

		@Override
		protected Class<? super T> targetClass() {
			return targetClass;
		}
	}

}
