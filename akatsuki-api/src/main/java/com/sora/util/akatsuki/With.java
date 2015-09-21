package com.sora.util.akatsuki;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation allows you to specify different {@link TypeConverter} for the
 * annotated element. All annotations concerning Bundle serializations such as
 * {@link Retained} and {@link Arg} will respect this.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface With {

	/**
	 * The specified {@link TypeConverter} will be used to persist this field
	 * (this takes precedence over everything, including the built in type
	 * support and {@link DeclaredConverter} annotated {@link TypeConverter}s)
	 */
	Class<? extends TypeConverter<?>>value() default TypeConverter.DummyTypeConverter.class;

	// adding more stuff might change the meaning of this annotation a bit,
	// let's try not to do that :)
}
