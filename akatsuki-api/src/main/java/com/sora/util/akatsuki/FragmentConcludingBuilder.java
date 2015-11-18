package com.sora.util.akatsuki;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;

import com.sora.util.akatsuki.Internal.ClassArgBuilder;

public class FragmentConcludingBuilder<T> extends ClassArgBuilder<T> {

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
