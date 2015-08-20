package com.sora.util.akatsuki;

import android.os.Bundle;

import java.util.Collection;

public class CollectionConverter implements TypeConverter<Collection<?>> {
	@Override
	public void save(Bundle bundle, Collection<?> collection, String key) {

	}

	@Override
	public Collection<?> restore(Bundle bundle, Collection<?> collection, String key) {
		return null;
	}
}
