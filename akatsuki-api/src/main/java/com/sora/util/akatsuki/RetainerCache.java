package com.sora.util.akatsuki;

/**
 * Used for caching class names to avoid reflection. This interface should be
 * implemented only by generated classes.
 */
public interface RetainerCache {

	<T> Class<? extends BundleRetainer<T>> getCached(String clazz);

}
