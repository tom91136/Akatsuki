package com.sora.util.akatsuki.app;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.sora.util.akatsuki.Akatsuki;
import com.sora.util.akatsuki.Retained;

import java.util.Random;

import akatsuki.util.sora.com.akatsuki.R;
import butterknife.Bind;
import butterknife.ButterKnife;

public class SerializeExampleFragment extends Fragment {

	@Bind(R.id.serialize) Button serialize;
	@Bind(R.id.deserialize) Button deserialize;
	@Bind(R.id.status) TextView status;

	private Bundle bundle;

	public SerializeExampleFragment() {
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_serialize_example, container, false);
		ButterKnife.bind(this, view);
		status.setMovementMethod(new ScrollingMovementMethod());
		serialize.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				NarratedBean instance = new NarratedBean(true);
				bundle = Akatsuki.serialize(instance);
				status.setText(status.getText() + "\nObject " + instance + " is serialized");
			}
		});

		deserialize.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (bundle == null) {
					status.setText(status.getText() + "\nNothing is serialized, bundle is empty!");
				} else {
					NarratedBean bean = Akatsuki.deserialize(new NarratedBean(false), bundle);
					status.setText(status.getText() + "\nObject restored, result = " + bean);
				}
			}
		});

		return view;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		ButterKnife.unbind(this);
	}

	static class BaseBean {

		@Retained int answer;

		public BaseBean(boolean random) {
			if (random)
				answer = new Random().nextInt();
		}
	}

	static class NarratedBean extends BaseBean {

		String narration = "The answer to life the universe and everything is ";

		public NarratedBean(boolean random) {
			super(random);
		}

		public String computeAnswer() {
			return narration + answer;
		}

		@Override
		public String toString() {
			return "NarratedBean{" + computeAnswer() + '}';
		}
	}

}
