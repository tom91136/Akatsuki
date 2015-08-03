/*
 * Copyright 2015 WEI CHEN LIN
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sora.util.akatsuki;

/**
 * Defines a constraint for type matching
 */
public @interface TypeConstraint {

	/**
	 * Matching classes for this constraint. Annotated class are also supported,
	 * simply supply the annotation class instead of the type.
	 */
	Class<?>[]types();

	/**
	 * Bounds for class type matching. (This has no effect on annotation classes
	 * because inheritance is not supported)
	 *
	 */
	Bound bound() default Bound.EXACTLY;

	/**
	 * Class matching bounds
	 */
	enum Bound {
		/**
		 * Has the same meaning of {@code A.class.equals(B.class)}
		 */
		EXACTLY,

		/**
		 * Has the same meaning of {@code A.class.isAssignableFrom(B.class)}
		 */

		EXTENDS,

		/**
		 * Has the same meaning of
		 * {@code A.class.equals(B.class.getSuperclass())} where the inheritance
		 * hierarchy is transversed and checked for equality all the way up to
		 * {@link Object}
		 */
		SUPER
	}

}
