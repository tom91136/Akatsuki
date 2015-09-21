package com.sora.util.akatsuki;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Global configuration for Akatsuki, You can put this annotation anywhere as
 * this annotation is only used at compile-time. Placing this annotation on the
 * {@link android.app.Application} class or the respective serialization target
 * type is recommended.
 */
@Target({ ElementType.TYPE, ElementType.FIELD, ElementType.PACKAGE, ElementType.ANNOTATION_TYPE })
public @interface AkatsukiConfig {

	/**
	 * Fields with the {@code transient} keyword will be ignored by default
	 */
	boolean allowTransient() default false;

	/**
	 * Fields with the {@code volatile} keyword will be ignored by default
	 */
	boolean allowVolatile() default false;

}
