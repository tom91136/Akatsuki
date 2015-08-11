package com.sora.util.akatsuki;

public @interface TypeFilter {

	TypeConstraint type();

	TypeConstraint[]parameters() default {};

}
