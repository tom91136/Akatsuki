package com.sora.util.akatsuki.app;

import android.os.Bundle;
import android.support.v4.app.Fragment;

import com.sora.util.akatsuki.Akatsuki;
import com.sora.util.akatsuki.DeclaredConverter;
import com.sora.util.akatsuki.Retained;
import com.sora.util.akatsuki.TypeConstraint;
import com.sora.util.akatsuki.TypeConverter;
import com.sora.util.akatsuki.TypeFilter;

public class BaseRetainedFragment extends Fragment {

	@Retained EssentialStuff stuff;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Akatsuki.restore(this, savedInstanceState);
		if(savedInstanceState == null){
			stuff = new EssentialStuff(42);
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Akatsuki.save(this, outState);
	}

	public static class EssentialStuff{
		 public final int theAnswer;

		EssentialStuff(int theAnswer) {
			this.theAnswer = theAnswer;
		}
	}

	@DeclaredConverter(@TypeFilter(type = @TypeConstraint(type=EssentialStuff.class)))
	public static class EssentialStuffConverter implements TypeConverter<EssentialStuff>{

		@Override
		public void save(Bundle bundle, EssentialStuff essentialStuff, String key) {
			bundle.putInt(key, essentialStuff.theAnswer);
		}

		@Override
		public EssentialStuff restore(Bundle bundle, EssentialStuff essentialStuff, String key) {
			return new EssentialStuff(bundle.getInt(key));
		}
	}
}
