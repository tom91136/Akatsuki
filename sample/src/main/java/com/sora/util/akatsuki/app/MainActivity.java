package com.sora.util.akatsuki.app;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import com.sora.util.akatsuki.Akatsuki;
import com.sora.util.akatsuki.Akatsuki.LoggingLevel;
import com.sora.util.akatsuki.IncludeClasses;
import com.sora.util.akatsuki.RetainConfig;
import com.sora.util.akatsuki.Retained;
import com.sora.util.akatsuki.Retained.RestorePolicy;
import com.sora.util.akatsuki.parceler.ParcelerSupport;

import java.util.ArrayList;

import akatsuki.util.sora.com.akatsuki.R;
import butterknife.Bind;
import butterknife.ButterKnife;
@RetainConfig(restorePolicy = RestorePolicy.IF_NULL)
@IncludeClasses(ParcelerSupport.class)
public class MainActivity extends AppCompatActivity {

	private static final String MY_KEY = "myString";
	@Retained String myString;
	@Retained Float myFloat = 3F;
	//@Retained ArrayList<String>[] aaa;
	//@Retained String[][][] bbb ={};

	@Bind(R.id.persisted) EditText persisted;

	{
		Akatsuki.setLoggingLevel(LoggingLevel.VERBOSE);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		ButterKnife.bind(this);

		Akatsuki.restore(this, savedInstanceState);

		persisted.setText(myString);
		persisted.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {

			}

			@Override
			public void afterTextChanged(Editable s) {
				myString = persisted.getText().toString();
			}
		});
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(MY_KEY, myString);
		Akatsuki.save(this, outState);
	}

}
