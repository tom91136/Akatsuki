package com.sora.util.akatsuki;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.sora.util.akatsuki.Retained.RestorePolicy;

/**
 * A compile time configuration annotation for Akatsuki, this can be placed on
 * any element
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.CLASS)
public @interface RetainConfig {

	/**
	 * Global {@link RestorePolicy} setting, see
	 * {@link Retained#restorePolicy()}
	 */
	RestorePolicy restorePolicy() default RestorePolicy.DEFAULT;

	/**
	 * Whether the annotated class should be processed; useful for debugging
	 */
	boolean enabled() default true;

}
