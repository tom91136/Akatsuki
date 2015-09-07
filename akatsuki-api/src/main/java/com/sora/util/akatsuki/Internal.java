package com.sora.util.akatsuki;

import android.app.Activity;
import android.app.Fragment;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.sora.util.akatsuki.Akatsuki.LoggingLevel;

/**
 * This is not the class you are looking for (unless you want to create your own
 * instance of {@link BundleRetainer}) for testing
 */
@SuppressWarnings("unused")
public class Internal {
	/**
	 * Finds the {@link BundleRetainer} class and instantiate it. You would not
	 * normally need this, this method does not do any caching and is designed
	 * to be used from classes that require access to a fresh
	 * {@link BundleRetainer} instance
	 *
	 * @param fqcn
	 *            the fully qualified class name of the instance
	 * @param clazz
	 *            the {@link Class} of the instance
	 * @param <T>
	 *            the type of the annotated instance
	 * @return the {@link BundleRetainer}
	 */
	@SuppressWarnings("unchecked")
	public static <T> BundleRetainer<T> createRetainer(ClassLoader loader, RetainerCache cache,
			String fqcn, Class<?> clazz) {
		final BundleRetainer<T> instance;
		try {
			Class<? extends BundleRetainer> retainerClass = null;
			if (cache != null)
				retainerClass = cache.getCached(fqcn);
			if (retainerClass == null) {
				try {
					retainerClass = (Class<? extends BundleRetainer>) Class
							.forName(generateRetainerClassName(fqcn), true, loader);
				} catch (ClassNotFoundException ignored) {
					// can't find it, moving on
				}
			}
			if (retainerClass == null)
				retainerClass = (Class<? extends BundleRetainer>) findClass(loader, clazz);
			if (retainerClass == null)
				throw new RuntimeException("Unable to find generated class for " + fqcn
						+ ", does the class contain any fields annotated with @Retain(inherited "
						+ "class works too)?");
			instance = retainerClass.newInstance();
		} catch (ClassCastException e) {
			throw new RuntimeException(
					fqcn + "does not implement BundleRetainer or has the wrong generic "
							+ "parameter, this is weird",
					e);
		} catch (Exception e) {
			throw new RuntimeException(e);
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
			if (Akatsuki.loggingLevel == LoggingLevel.VERBOSE)
				Log.i(Akatsuki.TAG, "traversing hieraichy to find retainer for class " + clazz);
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
	public static String generateRetainerClassName(CharSequence prefix) {
		return prefix + "$$" + BundleRetainer.class.getSimpleName();
	}

	public static abstract class ArgBuilder<T> {

		protected final Bundle bundle;
		protected final Class<T> targetClass;

		public ArgBuilder(Bundle bundle, Class<T> targetClass) {
			this.bundle = bundle;
			this.targetClass = targetClass;
		}

		public ArgBuilder(Class<T> targetClass) {
			this.targetClass = targetClass;
			this.bundle = new Bundle();
		}
	}

	public static class FragmentConcludingBuilder<T> extends ArgBuilder<T> {

		public FragmentConcludingBuilder(Bundle bundle, Class<T> targetClass) {
			super(bundle, targetClass);
		}

		public FragmentConcludingBuilder(Class<T> targetClass) {
			super(targetClass);
		}

		// TODO consider letting the processor generate the instantiation
		// statement for us, Fragment.instantiate uses reflection internally
		// which is somewhat bad
		@SuppressWarnings("unchecked")
		public T build(Context context) {
			if (targetClass.isAssignableFrom(Fragment.class))
				return (T) Fragment.instantiate(context, targetClass.getName(), bundle);
			else if (targetClass.isAssignableFrom(android.support.v4.app.Fragment.class)) {
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

	public static abstract class IntentConcludingBuilder<T> extends ArgBuilder<T> {

		public IntentConcludingBuilder(Bundle bundle, Class<T> targetClass) {
			super(bundle, targetClass);
		}

		public IntentConcludingBuilder(Class<T> targetClass) {
			super(targetClass);
		}

		protected Intent build() {
			return new Intent().putExtras(bundle);
		}

		public Intent build(Context context) {
			return build().setClass(context, targetClass);
		}

	}

	public static class ActivityConcludingBuilder<T extends Activity>
			extends IntentConcludingBuilder<T> {

		public ActivityConcludingBuilder(Bundle bundle, Class<T> targetClass) {
			super(bundle, targetClass);
		}

		public ActivityConcludingBuilder(Class<T> targetClass) {
			super(targetClass);
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

		public ServiceConcludingBuilder(Bundle bundle, Class<T> targetClass) {
			super(bundle, targetClass);
		}

		public ServiceConcludingBuilder(Class<T> targetClass) {
			super(targetClass);
		}

		public ComponentName startService(Context context) {
			return context.startService(build(context));
		}
	}

}
