package com.sora.util.akatsuki.app;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;
import android.widget.NumberPicker.OnValueChangeListener;

import com.sora.util.akatsuki.Akatsuki;
import com.sora.util.akatsuki.Retained;

import akatsuki.util.sora.com.akatsuki.R;
import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * A placeholder fragment containing a simple view.
 */

public class MainActivityFragment extends BaseRetainedFragment {

	@Retained int theValue;

	@Bind(R.id.numberPicker) NumberPicker picker;

	public MainActivityFragment() {
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.fragment_main, container, false);
		ButterKnife.bind(this, view);
		Akatsuki.restore(this, savedInstanceState);

		picker.setMinValue(0);
		picker.setMaxValue(100);
		picker.setOnValueChangedListener(new OnValueChangeListener() {
			@Override
			public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
				theValue = newVal;
			}
		});
		this.picker.setValue(theValue);

		return view;
	}


}
