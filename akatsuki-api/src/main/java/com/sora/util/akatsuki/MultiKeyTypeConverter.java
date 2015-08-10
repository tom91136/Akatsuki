package com.sora.util.akatsuki;

import android.os.Bundle;

/**
 * A converter that is immune to key collision
 */
public abstract class MultiKeyTypeConverter<T> implements TypeConverter<T> {

	private static final String KEYS = "BundleTypeConverter";

	@Override
	public final void save(Bundle bundle, T t, String key) {
		Bundle b = new Bundle();
		final String[] keys = saveMultiple(b, t);
		b.putStringArray(KEYS, keys);
		bundle.putBundle(key, b);
	}

	@Override
	public final T restore(Bundle bundle, T t, String key) {
		final Bundle b = bundle.getBundle(key);
		final String[] keys = b.getStringArray(KEYS);
		return restoreMultiple(b, t, keys);
	}

	/**
	 * Saves multiple elements into the bundle without having to define
	 * constants. Use {@link #generateKey(int)} to generate keys of desired size
	 * 
	 * @param bundle
	 *            the bundle to save to
	 * @param t
	 *            the instance
	 * @return the keys used
	 */
	protected abstract String[] saveMultiple(Bundle bundle, T t);

	/**
	 * Restores the given type
	 * 
	 * @param bundle
	 *            the bundle
	 * @param t
	 *            the instance
	 * @param keys
	 *            the keys from {@link #saveMultiple(Bundle, Object)} @return
	 *            the restored type
	 */
	protected abstract T restoreMultiple(Bundle bundle, T t, String[] keys);

	/**
	 * Generates an array of keys with the given size
	 */
	public static String[] generateKey(int size) {
		String[] keys = new String[size];
		for (int i = 0; i < size; i++)
			keys[i] = String.valueOf(i);
		return keys;
	}
}
