package com.sora.util.akatsuki;

import android.os.Bundle;

public final class DummyTypeConverter implements TypeConverter<Void> {

	@Override
	public void save(Bundle bundle, Void ignored, String key) {
		throw new RuntimeException("DummyTypeConverter should not be used directly");
	}

	@Override
	public Void restore(Bundle bundle, Void ignored, String key) {
		throw new RuntimeException("DummyTypeConverter should not be used directly");
	}

}
