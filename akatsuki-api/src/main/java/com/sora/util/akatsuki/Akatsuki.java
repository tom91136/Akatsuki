package com.sora.util.akatsuki;

import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Contains API for working with {@link Retained} annotated fields.
 */
public class Akatsuki {

	private static final WeakHashMap<String, BundleRetainer<?>> CLASS_CACHE = new WeakHashMap<>();

	private static final Map<Class<? extends TypeConverter<?>>, TypeConverter<?>> CACHED_CONVERTERS = new HashMap<>();

	private static final String TAG = "Akatsuki";

	/**
	 * Saves all fields annotated with {@link Retained} into the provided bundle
	 * 
	 * @param instance
	 *            the object that contains the annotated fields
	 * @param outState
	 *            the bundle for saving, not null
	 */
	public static void save(Object instance, Bundle outState) {
		if (outState == null)
			throw new IllegalArgumentException("outState cannot be null");
		getInstance(instance).save(instance, outState);
	}

	/**
	 * Restores field saved by {@link #save(Object, Bundle)} back into the
	 * instance
	 * 
	 * @param instance
	 *            the object that needs restoring
	 * @param savedInstanceState
	 *            the bundle containing the saved fields, null-safe
	 */
	public static void restore(Object instance, Bundle savedInstanceState) {
		if (savedInstanceState == null)
			return;
		getInstance(instance).restore(instance, savedInstanceState);
	}

	/**
	 * Like {@link #save(Object, Bundle)} but included some View state aware
	 * logic, use this if you want to save view states. Typical usage looks
	 * like:
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
		Bundle bundle = new Bundle();
		bundle.putParcelable(view.getClass().getName(), parcelable);
		save((Object) view, bundle);
		return bundle;
	}

	/**
	 * For restoring states saved by {@link #save(View, Parcelable)}.Typical
	 * usage looks like:
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
		if (parcelable instanceof Bundle) {
			final Bundle bundle = (Bundle) parcelable;
			restore((Object) view, bundle);
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
		restore(instance, bundle);
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
	 * 
	 */
	public static <T> T deserialize(InstanceSupplier<T> supplier, Bundle bundle) {
		final T t = supplier.create();
		return deserialize(t, bundle);
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

	@SuppressWarnings("unchecked")
	private static <T> BundleRetainer<T> getInstance(T clazz) {
		final String fqcn = clazz.getClass().getName();
		BundleRetainer<T> instance = (BundleRetainer<T>) CLASS_CACHE.get(fqcn);
		if (instance == null) {
			final String generatedClassName = generateRetainerClassName(fqcn);
			try {
				instance = (BundleRetainer<T>) Class.forName(generatedClassName).newInstance();
				CLASS_CACHE.put(fqcn, instance);
				Log.i(TAG, "cache miss for class " + fqcn + " (" + generatedClassName + ")");
			} catch (ClassCastException e) {
				throw new RuntimeException(
						fqcn + "does not implement BundleRetainer or has the wrong generic "
								+ "parameter, this is weird",
						e);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		} else {
			Log.i(TAG, "cache hit " + fqcn);
		}
		return instance;
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

	/**
	 * Create the name for the generated class. <b>This is not the method you
	 * are looking for</b>
	 * 
	 * @param prefix
	 *            the class name
	 * 
	 */
	public static String generateRetainerClassName(CharSequence prefix) {
		return prefix + "$$" + BundleRetainer.class.getSimpleName();
	}

}
