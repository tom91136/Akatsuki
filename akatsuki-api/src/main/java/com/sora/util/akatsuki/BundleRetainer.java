package com.sora.util.akatsuki;

import android.os.Bundle;

/**
 * Interface for all generated classes
 * @param <T> the type of annotated field's enclosing instance
 */
public interface BundleRetainer<T> {

	void save(T source, Bundle bundle);

	void restore(T source, Bundle bundle);

}
