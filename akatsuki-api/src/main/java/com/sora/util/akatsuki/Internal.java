package com.sora.util.akatsuki;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

import android.app.Activity;
import android.app.Fragment;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * This is not the class you are looking for (unless you want to create your own
 * instance of {@link BundleRetainer}) for testing
 */
@SuppressWarnings("unused")
public class Internal {

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
		final BundleRetainer<T> instance;
		final String fqcn = clazz.getName();
		try {
			Class<? extends BundleRetainer> retainerClass = null;
			// TODO we didn't cache Arg.class , implement it
			if (cache != null && type != Arg.class)
				retainerClass = cache.getCached(fqcn);
			if (retainerClass == null) {
				String className = null;
				try {
					if (type == Retained.class) {
						className = generateRetainerClassName(fqcn);
					} else if (type == Arg.class) {
						Package pkg = clazz.getPackage();
						String packageName = getPackageNameString(clazz);
						// give up trying, we're not getting the package name
						if (packageName == null) {
							throw new RuntimeException(
									"unable to obtain a package name from class " + clazz);
						}
						String builderClassName = clazz.getSimpleName() + "Builder";

						int indexOfDollar = builderClassName.lastIndexOf('$');
						if (indexOfDollar != -1) {
							builderClassName = builderClassName.substring(indexOfDollar+1 ,
									builderClassName.length());
						}

						className = generateRetainerClassName(packageName + ".Builders$"
								+ builderClassName + "$" + builderClassName);
						Log.i(Akatsuki.TAG, "bcn -> " + builderClassName);
					} else {
						throw new AssertionError(
								"Unable to create retainer for unknown class " + type);
					}

					retainerClass = (Class<? extends BundleRetainer>) Class.forName(className, true,
							loader);
				} catch (ClassNotFoundException ignored) {
					Log.i(Akatsuki.TAG, "Retainer class does not exist for fqcn " + className
							+ " trying inheritance next");
					// can't find it, moving on
				}
			}
			// recursive
			if (retainerClass == null && type == Retained.class)
				retainerClass = (Class<? extends BundleRetainer>) findClass(loader, clazz);
			if (retainerClass == null)
				throw new RuntimeException("Unable to find generated class for " + fqcn
						+ " while traversing the class hierarchy."
						+ "\nYou cannot call Akatsuki.save/restore with classes that does not have fields annotated with @Retained."
						+ "\nIf proguard is turned on, please add the respective rules for Akatsuki.");
			instance = retainerClass.newInstance();
		} catch (ClassCastException e) {
			throw new AssertionError(
					fqcn + "does not implement BundleRetainer or has the wrong generic "
							+ "parameter, this should not happen at all",
					e);
		} catch (Exception e) {
			throw new RuntimeException(
					"Something unexpected happened while creating the retainer instance", e);
		}
		return instance;
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

	static String generateBuilderClassName(CharSequence prefix) {
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

	public static class FragmentConcludingBuilder<T> extends ClassArgBuilder<T> {

		public FragmentConcludingBuilder(Bundle bundle, Class<? super T> targetClass) {
			super(bundle, targetClass);
		}

		// TODO consider letting the processor generate the instantiation
		// statement for us, Fragment.instantiate uses reflection internally
		// which is somewhat bad
		@SuppressWarnings("unchecked")
		public T build(Context context) {
			Class<? super T> targetClass = targetClass();
			if (Fragment.class.isAssignableFrom(targetClass))
				return (T) Fragment.instantiate(context, targetClass.getName(), bundle);
			else if (android.support.v4.app.Fragment.class.isAssignableFrom(targetClass)) {
				return (T) android.support.v4.app.Fragment.instantiate(context,
						targetClass.getName(), bundle);
			} else {
				throw new AssertionError("Target class of " + targetClass
						+ " is neither android.app.Fragment or android.support.v4.app.Fragment, "
						+ "this error should have been caught by the processor and should not happen");
			}
		}

		public Bundle buildArgs() {
			return bundle;
		}

	}

	public static abstract class IntentConcludingBuilder<T> extends ClassArgBuilder<T> {

		public IntentConcludingBuilder(Bundle bundle, Class<? super T> targetClass) {
			super(bundle, targetClass);
		}

		protected Intent build() {
			return new Intent().putExtras(bundle);
		}

		public Intent build(Context context) {
			return build().setClass(context, targetClass());
		}

	}

	public static class ActivityConcludingBuilder<T extends Activity>
			extends IntentConcludingBuilder<T> {

		public ActivityConcludingBuilder(Bundle bundle, Class<? super T> targetClass) {
			super(bundle, targetClass);
		}

		public void startActivity(Context context) {
			context.startActivity(build(context));
		}

		public void startActivity(Context context, Bundle activityOptions) {
			context.startActivity(build(context), activityOptions);
		}

	}

	public static class ServiceConcludingBuilder<T extends Service>
			extends IntentConcludingBuilder<T> {

		public ServiceConcludingBuilder(Bundle bundle, Class<? super T> targetClass) {
			super(bundle, targetClass);
		}

		public ComponentName startService(Context context) {
			return context.startService(build(context));
		}
	}

}
