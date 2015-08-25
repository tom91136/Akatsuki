package com.sora.util.akatsuki;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Retains annotated field through a {@link android.os.Bundle}. To save or
 * restore, see {@link Akatsuki}
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface Retained {

	/**
	 * Rules on how a field will be restored from a bundle.
	 */
	enum RestorePolicy {
		/**
		 * Default restore policy, same as {@link #OVERWRITE}
		 */
		DEFAULT, /**
					 * Field will be restored regardless of the field or the key
					 * state
					 */
		OVERWRITE, /**
					 * Field will be restored only if the target field is null
					 */
		IF_NULL, /**
					 * Field will be restored only if the target field is not
					 * null
					 */
		IF_NOT_NULL
	}

	/**
	 * This field will not be retained
	 */
	boolean skip() default false;

	/**
	 * Applies a {@link com.sora.util.akatsuki.Retained.RestorePolicy} on the
	 * restoration of the field. Defaults to
	 * {@link com.sora.util.akatsuki.Retained.RestorePolicy#OVERWRITE}
	 */
	RestorePolicy restorePolicy() default RestorePolicy.DEFAULT;

	/**
	 * The specified {@link TypeConverter} will be used to persist this field
	 * (this takes precedence over everything, including the built in type
	 * support and {@link DeclaredConverter} annotated {@link TypeConverter}s)
	 */
	Class<? extends TypeConverter<?>>converter() default DummyTypeConverter.class;

}
