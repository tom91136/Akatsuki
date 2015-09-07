package com.sora.util.akatsuki;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface Def {

	/**
	 * The specified {@link TypeConverter} will be used to persist this field
	 * (this takes precedence over everything, including the built in type
	 * support and {@link DeclaredConverter} annotated {@link TypeConverter}s)
	 */
	Class<? extends TypeConverter<?>>value() default DummyTypeConverter.class;
}
