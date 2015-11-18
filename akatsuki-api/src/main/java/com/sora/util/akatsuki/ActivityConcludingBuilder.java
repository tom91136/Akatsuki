package com.sora.util.akatsuki;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

public class ActivityConcludingBuilder<T extends Activity>
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
