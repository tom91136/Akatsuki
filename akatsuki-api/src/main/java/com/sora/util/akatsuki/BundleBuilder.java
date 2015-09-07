package com.sora.util.akatsuki;

import android.os.Bundle;
import android.os.Parcelable;
import android.util.SparseArray;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * A convenience class for you to build {@link Bundle}s with a chained builder
 */
public class BundleBuilder {

	private final Bundle bundle = new Bundle();

	public BundleBuilder add(Bundle map) {
		bundle.putAll(map);
		return this;
	}

	public BundleBuilder add(String key, boolean value) {
		bundle.putBoolean(key, value);
		return this;
	}

	public BundleBuilder add(String key, byte value) {
		bundle.putByte(key, value);
		return this;
	}

	public BundleBuilder add(String key, char value) {
		bundle.putChar(key, value);
		return this;
	}

	public BundleBuilder add(String key, short value) {
		bundle.putShort(key, value);
		return this;
	}

	public BundleBuilder add(String key, int value) {
		bundle.putInt(key, value);
		return this;
	}

	public BundleBuilder add(String key, long value) {
		bundle.putLong(key, value);
		return this;
	}

	public BundleBuilder add(String key, float value) {
		bundle.putFloat(key, value);
		return this;
	}

	public BundleBuilder add(String key, double value) {
		bundle.putDouble(key, value);
		return this;
	}

	public BundleBuilder add(String key, String value) {
		bundle.putString(key, value);
		return this;
	}

	public BundleBuilder add(String key, CharSequence value) {
		bundle.putCharSequence(key, value);
		return this;
	}

	public BundleBuilder add(String key, Parcelable value) {
		bundle.putParcelable(key, value);
		return this;
	}

	public BundleBuilder add(String key, Parcelable[] value) {
		bundle.putParcelableArray(key, value);
		return this;
	}

	public BundleBuilder addParcelableArrayList(String key, ArrayList<? extends Parcelable> value) {
		bundle.putParcelableArrayList(key, value);
		return this;
	}

	public BundleBuilder add(String key, SparseArray<? extends Parcelable> value) {
		bundle.putSparseParcelableArray(key, value);
		return this;
	}

	public BundleBuilder addIntegerArrayList(String key, ArrayList<Integer> value) {
		bundle.putIntegerArrayList(key, value);
		return this;
	}

	public BundleBuilder addStringArrayList(String key, ArrayList<String> value) {
		bundle.putStringArrayList(key, value);
		return this;
	}

	public BundleBuilder addCharSequenceList(String key, ArrayList<CharSequence> value) {
		bundle.putCharSequenceArrayList(key, value);
		return this;
	}

	public BundleBuilder add(String key, Serializable value) {
		bundle.putSerializable(key, value);
		return this;
	}

	public BundleBuilder add(String key, boolean[] value) {
		bundle.putBooleanArray(key, value);
		return this;
	}

	public BundleBuilder add(String key, byte[] value) {
		bundle.putByteArray(key, value);
		return this;
	}

	public BundleBuilder add(String key, short[] value) {
		bundle.putShortArray(key, value);
		return this;
	}

	public BundleBuilder add(String key, char[] value) {
		bundle.putCharArray(key, value);
		return this;
	}

	public BundleBuilder add(String key, int[] value) {
		bundle.putIntArray(key, value);
		return this;
	}

	public BundleBuilder add(String key, long[] value) {
		bundle.putLongArray(key, value);
		return this;
	}

	public BundleBuilder add(String key, float[] value) {
		bundle.putFloatArray(key, value);
		return this;
	}

	public BundleBuilder add(String key, double[] value) {
		bundle.putDoubleArray(key, value);
		return this;
	}

	public BundleBuilder add(String key, String[] value) {
		bundle.putStringArray(key, value);
		return this;
	}

	public BundleBuilder add(String key, CharSequence[] value) {
		bundle.putCharSequenceArray(key, value);
		return this;
	}

	public BundleBuilder add(String key, Bundle value) {
		bundle.putBundle(key, value);
		return this;
	}

	public Bundle build() {
		return bundle;
	}
}
