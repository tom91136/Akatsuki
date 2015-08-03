package com.sora.util.akatsuki;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Retains annotated field through a {@link android.os.Bundle}. To save or restore, see {@link Akatsuki}
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface Retained {

	/**
	 * This field will not be retained
	 */
	boolean skip() default false;

	/**
	 * The specified {@link TypeConverter} will be used to persist this field
	 * (this takes precedence over everything, including the built in type
	 * support and {@link DeclaredConverter} annotated {@link TypeConverter}s)
	 */
	Class<? extends TypeConverter<?>>converter() default DummyTypeConverter.class;

}
