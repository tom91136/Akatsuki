package com.sora.util.akatsuki.app;

import android.os.Bundle;
import android.support.v4.app.Fragment;

import com.sora.util.akatsuki.Akatsuki;
import com.sora.util.akatsuki.Arg;
import com.sora.util.akatsuki.ArgConfig;
import com.sora.util.akatsuki.ArgConfig.BuilderType;

@ArgConfig(type = BuilderType.CHECKED)
public class ArgRetainedFragment extends Fragment {

	@Arg int a;
	@Arg double b;
	@Arg double c;
	@Arg(optional = true) int d;
	@Arg(optional = true) int e;
	@Arg boolean f;
	@Arg(optional = true) boolean g;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Akatsuki.restore(this, savedInstanceState);



	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Akatsuki.save(this, outState);
	}
}
