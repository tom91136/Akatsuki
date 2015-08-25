package com.sora.util.akatsuki;

import android.os.Bundle;

import com.sora.util.akatsuki.Akatsuki.LoggingLevel;
import com.sora.util.akatsuki.Retained.RestorePolicy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A compile time configuration annotation for Akatsuki, this can be placed on
 * any element
 */
@Target({ ElementType.TYPE, ElementType.FIELD, ElementType.PACKAGE, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.CLASS)
public @interface RetainConfig {

	enum Optimisation {

		/**
		 * No caching will be done at all, all generated classes will be created
		 * fresh whenever used. This is only preferable under devices with
		 * <b>extreme</b> memory constraints. Reflection will be used on every
		 * invocation of either {@link Akatsuki#save(Object, Bundle)} or
		 * {@link Akatsuki#restore(Object, Bundle)} and dependent methods
		 */
		NONE,

		/**
		 * Any class that contains any field marked with {@link Retained} will
		 * be cached to improve performance. Any inherited classes will have to
		 * traverse the class hierarchy to find the correct generated
		 * class.Reflection will be used only once when the generated class is
		 * first instantiated. The generated class instance will then be cached
		 * in memory.
		 */
		ANNOTATED, /**
					 * All class that contains field marked with
					 * {@link Retained}(including inherited, direct or indirect)
					 * will be cached. Reflection will be used only once at app
					 * start-up where the {@link RetainerCache} will be located.
					 */
		ALL
	}

	/**
	 * Specifies the optimisation level, defaults to {@link Optimisation#ALL}
	 */
	Optimisation optimisation() default Optimisation.ALL;

	/**
	 * Global {@link RestorePolicy} setting, see
	 * {@link Retained#restorePolicy()}
	 */
	RestorePolicy restorePolicy() default RestorePolicy.DEFAULT;

	/**
	 * Compile time logging options
	 */
	LoggingLevel loggingLevel() default LoggingLevel.ERROR_ONLY;

}
