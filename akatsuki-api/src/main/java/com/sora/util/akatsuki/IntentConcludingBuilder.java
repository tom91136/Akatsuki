package com.sora.util.akatsuki;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.sora.util.akatsuki.Internal.ClassArgBuilder;

public abstract class IntentConcludingBuilder<T> extends ClassArgBuilder<T> {

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
