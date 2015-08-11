package com.sora.util.akatsuki;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Project: Akatsuki
 * Created by tom91136 on 15/07/2015.
 */

/**
 * An annotation for specifying transformation templates. This is useful when
 * you have types that require special procedures when serializing. You can put
 * this annotation anywhere as this annotation is only used at compile-time. Placing this
 * annotation on the {@link android.app.Application} class or the respective
 * serialization target type is recommended.
 */
@Target({ ElementType.TYPE, ElementType.FIELD, ElementType.PACKAGE, ElementType.ANNOTATION_TYPE })
// the annotation needs to be in the class file otherwise compiled plugins would
// fail
@Retention(RetentionPolicy.CLASS)
public @interface TransformationTemplate {

	/**
	 * The template for saving (e.g. outState.putInt("myInt", myInt);). The
	 * template uses mustache. <p>
	 * Provided variables:
	 * <ol>
	 * <li>{{fieldName}} - the field name of the variable to be saved</li>
	 * <li>{{keyName}} - a unique key that does not collide with other keys</li>
	 * <li>{{bundle}} - the bundle</li>
	 * </ol>
	 * Sample usage:
	 * 
	 * <pre>
	 *     {@code {{bundle}}.putInt(\"{{fieldName}}\", {{fieldName}});\n}
	 * </pre>
	 * 
	 * Assuming {{fieldName}} is myInt, the template above will produce
	 * 
	 * <pre>
	 *     {@code bundle.putInt("myInt", myInt);}
	 * </pre>
	 *
	 */
	String save();

	/**
	 * The restore template. See {@link #save()} for explanation on how
	 * templates work. <p> Provided variables:
	 * <ol>
	 * <li>{{fieldName}} - the field name of the variable to be saved</li>
	 * <li>{{keyName}} - a unique key that does not collide with other keys</li>
	 * <li>{{bundle}} - the bundle</li>
	 * </ol>
	 */
	String restore();

	/**
	 * Defines how types are matched, see {@link TypeFilter} for more
	 * details on how to use
	 *
	 */
	TypeFilter[]filters();

	/**
	 * Execution time of the custom template. Setting this to "BEFORE" while
	 * overriding any of the built-in types could cause unforeseen issues
	 *
	 */
	Execution execution() default Execution.BEFORE;

	/**
	 * Execution times for the template
	 */
	enum Execution {
		/**
		 * The template is applied before any of the built-in type converter is
		 * invoked
		 */
		BEFORE,

		/**
		 * The template is ignored and not applied at all. This is useful for
		 * temporarily disabling misbehaving templates while debugging
		 */
		NEVER
	}

}
