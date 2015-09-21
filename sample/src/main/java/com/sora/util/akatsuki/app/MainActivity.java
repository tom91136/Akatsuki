package com.sora.util.akatsuki.app;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import com.sora.util.akatsuki.Akatsuki;
import com.sora.util.akatsuki.Akatsuki.LoggingLevel;
import com.sora.util.akatsuki.Arg;
import com.sora.util.akatsuki.ArgConcludingBuilder;
import com.sora.util.akatsuki.TypeConverter.DummyTypeConverter;
import com.sora.util.akatsuki.IncludeClasses;
import com.sora.util.akatsuki.RetainConfig;
import com.sora.util.akatsuki.Retained;
import com.sora.util.akatsuki.Retained.RestorePolicy;
import com.sora.util.akatsuki.With;
import com.sora.util.akatsuki.app.Builders.ArgRetainedFragmentBuilder;
import com.sora.util.akatsuki.parceler.ParcelerSupport;

import akatsuki.util.sora.com.akatsuki.R;
import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

@RetainConfig(restorePolicy = RestorePolicy.IF_NULL)
@IncludeClasses(ParcelerSupport.class)
public class MainActivity extends AppCompatActivity {

	@Retained @Arg String myString;
	@Retained Float myFloat = 3F;
	// @Retained ArrayList<String>[] aaa;
	// @Retained String[][][] bbb ={};

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

	@OnClick(R.id.start)
	public void start() {
		Builders.MainActivity().myString(myString).startActivity(this);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Akatsuki.save(this, outState);
	}

}
