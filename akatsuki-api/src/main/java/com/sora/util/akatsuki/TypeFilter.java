package com.sora.util.akatsuki;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a type filter
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface TypeFilter {

	/**
	 * The constraint of the type (eg: List.class will match {@code List
	 * <String>})
	 */
	TypeConstraint type();

	/**
	 * The generic parameter constraint of the type (eg: String will match
	 * {@code List<String>})
	 */
	TypeConstraint[]parameters() default {};

}
