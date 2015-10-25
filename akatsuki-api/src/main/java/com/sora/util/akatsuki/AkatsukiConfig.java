package com.sora.util.akatsuki;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Global configuration for Akatsuki, You can put this annotation anywhere;
 * placing this annotation on the {@link android.app.Application} class or the
 * respective serialization target type is recommended.
 */
@Target({ ElementType.TYPE, ElementType.FIELD, ElementType.PACKAGE, ElementType.ANNOTATION_TYPE })
@Retention(value = RetentionPolicy.RUNTIME) // we need to read the logging level
											// and the CACHE_INSTANCES flag
public @interface AkatsukiConfig {

	/**
	 * Logging levels
	 */
	enum LoggingLevel {

		/**
		 * Print debug information at compile time such as class scanning
		 * progress(this would large output so use with caution)
		 **/
		DEBUG,

		/**
		 * Print everything! (class caching, verification, and class hierarchy
		 * traversal attempts)
		 */
		VERBOSE,

		/**
		 * Print warning
		 */
		WARN,

		/**
		 * Prints only errors (when a {@link BundleRetainer} is missing for
		 * example)
		 */
		ERROR_ONLY
	}

	/**
	 * Optimisation flags for both the compilation and runtime phase.
	 */
	enum OptFlags {

		/**
		 * Creates a look up table for classes to their respective instances,
		 * eliminates the use of reflection. <br>
		 * Additional classes will be generated when this is enabled
		 */
		CLASS_LUT,

		/**
		 * Eliminate runtime traverse of the inheritance hierarchy. To be used
		 * in conjunction with {@link #CLASS_LUT} only, this flag adds
		 * additional entries to the table
		 */
		VECTORIZE_INHERITANCE,

		/**
		 * Caches created instances in memory; instances are weakly referenced
		 * to prevent memory leak
		 */
		CACHE_INSTANCES,

		/**
		 * Compacts the source by using inheritance(following the original
		 * inheritance tree)
		 */
		TREE_COMPACTION

	}

	/**
	 * Normal flags that changes the behavior of the compiler
	 */
	enum Flags {

		/**
		 * Warnings issued will be treated as error and stop the compilation
		 */
		WARNING_AS_ERROR,

		/**
		 * Performs a dry run by not writing any of the generated codes
		 */
		ANALYSE_ONLY,

		/**
		 * Disables the entire compiler, all annotations will be ignored. This
		 * is useful for debugging
		 */
		DISABLE_COMPILER

	}

	/**
	 * Compile time and runtime logging options
	 */
	LoggingLevel loggingLevel() default LoggingLevel.ERROR_ONLY;

	/**
	 * Fields with the {@code transient} keyword will be ignored by default
	 */
	boolean allowTransient() default false;

	/**
	 * Fields with the {@code volatile} keyword will be ignored by default
	 */
	boolean allowVolatile() default false;

	/**
	 * Sets the global default for {@link ArgConfig}
	 */
	ArgConfig argConfig() default @ArgConfig
	;

	/**
	 * Sets the global default for {@link RetainConfig}
	 */
	RetainConfig retainConfig() default @RetainConfig
	;

	/**
	 * Optimisation flags for the compiler, see
	 * {@link com.sora.util.akatsuki.AkatsukiConfig.OptFlags} for details. You
	 * can specify multiple flags, repeated flags will throw a warning
	 */
	OptFlags[]optFlags() default { OptFlags.CLASS_LUT, OptFlags.VECTORIZE_INHERITANCE,
			OptFlags.CACHE_INSTANCES, OptFlags.TREE_COMPACTION };

	/**
	 * Flags for the compiler, see
	 * {@link com.sora.util.akatsuki.AkatsukiConfig.Flags} for details. You can
	 * specify multiple flags, repeated flags will throw a warning
	 */
	Flags[]flags() default {};

}
