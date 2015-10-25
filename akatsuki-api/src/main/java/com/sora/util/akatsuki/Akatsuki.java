package com.sora.util.akatsuki;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.WeakHashMap;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.View;

import com.sora.util.akatsuki.AkatsukiConfig.LoggingLevel;

/**
 * Contains API for working with {@link Retained} annotated fields.
 */
@SuppressWarnings("ALL")
public class Akatsuki {

	private static final Map<String, BundleRetainer<?>> CLASS_CACHE = new WeakHashMap<>();

	private static final Map<Class<? extends TypeConverter<?>>, TypeConverter<?>> CACHED_CONVERTERS = new WeakHashMap<>();

	public static final String RETAINER_CACHE_NAME = "AkatsukiMapping";
	public static final String RETAINER_CACHE_PACKAGE = "com.sora.util.akatsuki";

	public static final String TAG = "Akatsuki";

	static LoggingLevel loggingLevel = AkatsukiConfig.LoggingLevel.ERROR_ONLY;

	private static RetainerCache retainerCache;

	static {
		Class<?> retainerCacheClass = null;
		try {
			retainerCacheClass = Class.forName(RETAINER_CACHE_PACKAGE + "." + RETAINER_CACHE_NAME);
		} catch (ClassNotFoundException iggored) {
			// we don't have it, that's fine
		}
		if (retainerCacheClass != null) {
			try {
				retainerCache = (RetainerCache) retainerCacheClass.newInstance();
			} catch (Exception e) {
				// we have it but it's broken, not good
				throw new RuntimeException("Unable to instantiate RetainerCache", e);
			}
		}
	}

	/**
	 * Sets the current logging level
	 */
	public static void setLoggingLevel(LoggingLevel level) {
		loggingLevel = level;
	}

	public static LoggingLevel loggingLevel() {
		return loggingLevel;
	}

	/**
	 * Saves all fields annotated with {@link Retained} into the provided bundle
	 *
	 * @param instance
	 *            the object that contains the annotated fields
	 * @param outState
	 *            the bundle for saving, not null
	 */
	public static void save(Object instance, Bundle outState) {
		save(classLoader(), instance, outState);
	}

	static void save(ClassLoader loader, Object instance, Bundle outState) {
		checkInstance(instance, "instance");
		checkInstance(outState, "outState");
		findRetainerInstance(loader, instance, Retained.class).save(instance, outState);
	}

	// all restores below work with @Retained and @Arg

	public static void restore(Fragment fragment, Bundle savedInstanceState) {
		restore(classLoader(), fragment, savedInstanceState);
	}

	static void restore(ClassLoader loader, Fragment fragment, Bundle savedInstanceState) {
		checkInstance(fragment, "fragment");
		restore(loader, fragment, savedInstanceState, fragment.getArguments());
	}

	public static void restore(android.app.Fragment fragment, Bundle savedInstanceState) {
		restore(classLoader(), fragment, savedInstanceState);

	}

	static void restore(ClassLoader loader, android.app.Fragment fragment,
			Bundle savedInstanceState) {
		checkInstance(fragment, "fragment");
		restore(loader, fragment, savedInstanceState, fragment.getArguments());
	}

	public static void restore(Activity activity, Bundle savedInstanceState) {
		restore(classLoader(), activity, savedInstanceState);
	}

	static void restore(ClassLoader loader, Activity activity, Bundle savedInstanceState) {
		checkInstance(activity, "activity");
		Intent intent = activity.getIntent();
		restore(loader, activity, savedInstanceState, intent != null ? intent.getExtras() : null);
	}

	/**
	 * Restores the given intent to the service
	 *
	 * @param service
	 *            the given service
	 * @param intent
	 *            the intent from
	 *            {@link Service#onStartCommand(Intent, int, int)}
	 */
	public static void restore(Service service, Intent intent) {
		restore(classLoader(), service, intent);
	}

	static void restore(ClassLoader loader, Service service, Intent intent) {
		checkInstance(service, "service");
		findRetainerInstance(loader, service, Arg.class).restore(service, intent.getExtras());
	}

	/**
	 * Restores the arguments and states from the bundles to the given instance
	 *
	 * @param instance
	 * @param state
	 * @param argument
	 */
	public static void restore(Object instance, Bundle state, Bundle argument) {
		restore(classLoader(), instance, state, argument);
	}

	static void restore(ClassLoader loader, Object instance, Bundle state, Bundle argument) {
		checkInstance(instance, "instance");
		if (state != null)
			findRetainerInstance(loader, instance, Retained.class).restore(instance, state);
		if (argument != null)
			findRetainerInstance(loader, instance, Arg.class).restore(instance, argument);
	}

	/**
	 * Like {@link #save(Object, Bundle)} but included some View state aware
	 * logic, use this if you want to save view states. Typical usage looks
	 * like:
	 * <p>
	 * 
	 * <pre>
	 * {@code
	 * &#64;Override
	 * protected Parcelable onSaveInstanceState() {
	 *  return Akatsuki.save(this, super.onSaveInstanceState());
	 * }
	 * }
	 * </pre>
	 *
	 * @param view
	 *            the view containing annotated fields
	 * @param parcelable
	 *            from {@code super.onSaveInstanceState()}
	 * @return a parcelable to returned in {@link View#onSaveInstanceState()}
	 */
	public static Parcelable save(View view, Parcelable parcelable) {
		return save(classLoader(), view, parcelable);
	}

	static Parcelable save(ClassLoader loader, View view, Parcelable parcelable) {
		checkInstance(view, "view");
		Bundle bundle = new Bundle();
		bundle.putParcelable(view.getClass().getName(), parcelable);
		save(loader, (Object) view, bundle);
		return bundle;
	}

	/**
	 * For restoring states saved by {@link #save(View, Parcelable)}.Typical
	 * usage looks like:
	 * <p>
	 * 
	 * <pre>
	 * {@code
	 * &#64;Override
	 * protected void onRestoreInstanceState(Parcelable state) {
	 *  super.onRestoreInstanceState(Akatsuki.restore(this, state));
	 * }
	 * }
	 * </pre>
	 *
	 * @param view
	 *            the view that requires restoring
	 * @param parcelable
	 *            restored state from the parameter of
	 *            {@link View#onRestoreInstanceState(Parcelable)}
	 * @return a parcelable to be passed to
	 *         {@code super.onRestoreInstanceState()}
	 */
	public static Parcelable restore(View view, Parcelable parcelable) {
		return restore(classLoader(), view, parcelable);
	}

	static Parcelable restore(ClassLoader loader, View view, Parcelable parcelable) {
		if (parcelable instanceof Bundle) {
			final Bundle bundle = (Bundle) parcelable;
			restore(loader, (Object) view, bundle, null);
			return bundle.getParcelable(view.getClass().getName());
		} else {
			throw new RuntimeException("View state of view " + view.getClass()
					+ " is not saved with Akatsuki View.onSaveInstanceState()");
		}
	}

	/**
	 * Serializes the given instance into a Bundle
	 */
	public static Bundle serialize(Object instance) {
		return serialize(classLoader(), instance);
	}

	static Bundle serialize(ClassLoader loader, Object instance) {
		Bundle bundle = new Bundle();
		save(instance, bundle);
		return bundle;
	}

	/**
	 * Deserialize the given bundle into the original instance
	 *
	 * @param instance
	 *            the instantiated instance
	 * @param bundle
	 *            the bundle
	 * @return deserialized instance
	 */
	public static <T> T deserialize(T instance, Bundle bundle) {
		return deserialize(classLoader(), instance, bundle);
	}

	static <T> T deserialize(ClassLoader loader, T instance, Bundle bundle) {
		restore(loader, instance, bundle, null);
		return instance;
	}

	/**
	 * Same as {@link #deserialize(Object, Bundle)} but with a
	 * {@link InstanceSupplier} so that the instance can be instantiated without
	 * creating an instance first
	 *
	 * @param supplier
	 *            the instance supplier
	 * @param bundle
	 *            the bundle
	 * @return deserialized instance
	 */
	public static <T> T deserialize(InstanceSupplier<T> supplier, Bundle bundle) {
		final T t = supplier.create();
		return deserialize(t, bundle);
	}

	private static void checkInstance(Object object, String name) {
		if (object == null)
			throw new IllegalArgumentException(name + " == null!");
	}

	/**
	 * An interface that supplies {@link #deserialize(InstanceSupplier, Bundle)}
	 * an working instance
	 *
	 * @param <T>
	 *            the type of the instance
	 */
	public interface InstanceSupplier<T> {
		/**
		 * Creates the instance
		 */
		T create();
	}

	static <T> BundleRetainer<T> findRetainerInstance(ClassLoader loader, T instance,
			Class<? extends Annotation> type) {
		final String fqcn = instance.getClass().getName();
		String retainerKey = generateRetainerKey(instance.getClass(), type);
		if (loggingLevel == AkatsukiConfig.LoggingLevel.VERBOSE)
			Log.i(TAG, "looking through cache with key " + retainerKey);
		BundleRetainer<T> retainer = (BundleRetainer<T>) CLASS_CACHE.get(retainerKey);
		if (retainer == null) {
			retainer = Internal.createRetainer(loader, retainerCache, instance.getClass(), type);
			CLASS_CACHE.put(retainerKey, retainer);
			if (loggingLevel == AkatsukiConfig.LoggingLevel.VERBOSE)
				Log.i(TAG, "cache miss for class " + fqcn + " for type " + type + " retainer is "
						+ retainer.getClass());
		} else {
			if (loggingLevel == AkatsukiConfig.LoggingLevel.VERBOSE)
				Log.i(TAG, "cache hit for class " + fqcn + " for type " + type + " retainer is "
						+ retainer.getClass());
		}
		return retainer;
	}

	private static ClassLoader classLoader() {
		return Thread.currentThread().getContextClassLoader();
	}

	private static String generateRetainerKey(Class<?> clazz, Class<? extends Annotation> type) {
		return clazz.getName() + "_" + type.getName();
	}

	private static void discardCache() {
		CLASS_CACHE.clear();
	}

	/**
	 * Finds the converter from the cache or create one. <b>This is not the
	 * method you are looking for</b>
	 *
	 * @param key
	 *            the class of the converter
	 */
	@SuppressWarnings("unchecked")
	public static <T> TypeConverter<T> converter(Class<? extends TypeConverter<T>> key) {
		TypeConverter<T> converter = (TypeConverter<T>) CACHED_CONVERTERS.get(key);
		if (converter == null) {
			try {
				converter = key.newInstance();
			} catch (Exception e) {
				converter = new InvalidTypeConverter(e);
			}
			CACHED_CONVERTERS.put(key, converter);
		}
		return converter;
	}

}
