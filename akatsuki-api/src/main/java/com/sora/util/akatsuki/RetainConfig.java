package com.sora.util.akatsuki;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.sora.util.akatsuki.Retained.RestorePolicy;

/**
 * Compile time configuration for {@link Retained}, to be used on classes with
 * field annotated with {@code Retained}
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.CLASS)
public @interface RetainConfig {

	/**
	 * Default {@link RestorePolicy} setting for the class, see
	 * {@link Retained#restorePolicy()}
	 */
	RestorePolicy restorePolicy() default RestorePolicy.DEFAULT;

	/**
	 * Whether the class should be processed; useful for debugging
	 */
	boolean enabled() default true;

}
