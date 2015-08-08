package com.sora.util.akatsuki;

public interface RetainerCache {

	<T> Class<? extends BundleRetainer<T>> getCached(String clazz);

}
