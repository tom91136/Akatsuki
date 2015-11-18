package com.sora.util.akatsuki;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;

public class ServiceConcludingBuilder<T extends Service>
		extends IntentConcludingBuilder<T> {

	public ServiceConcludingBuilder(Bundle bundle, Class<? super T> targetClass) {
		super(bundle, targetClass);
	}

	public ComponentName startService(Context context) {
		return context.startService(build(context));
	}
}
