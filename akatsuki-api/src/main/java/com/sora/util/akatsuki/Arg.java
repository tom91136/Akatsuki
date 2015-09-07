package com.sora.util.akatsuki;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface Arg {

	int NO_INDEX = -1;

	/**
	 * This field will not be used in the argument builder
	 */
	boolean skip() default false;

	/**
	 * This field will be optional in the argument builder
	 */
	boolean optional() default false;

	/**
	 * Use the given value as setter name of the builder, defaults to field name
	 */
	String value() default "";

	/**
	 * Custom index for field processing order
	 */
	int index() default NO_INDEX;



}
