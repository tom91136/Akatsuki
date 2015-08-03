package com.sora.util.akatsuki;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Includes a {@link TransformationTemplate} from another library so that the
 * compiler can see the annotation
 */
@Target({ ElementType.TYPE, ElementType.FIELD, ElementType.PACKAGE, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.CLASS)
public @interface IncludeClasses {

	Class<?>[]value();
}
