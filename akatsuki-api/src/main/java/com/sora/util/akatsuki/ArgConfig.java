package com.sora.util.akatsuki;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.sora.util.akatsuki.ArgConcludingBuilder.VoidBuilder;

/**
 * Compile time configuration for {@link Arg}, to be used on classes with field
 * annotated with {@code Arg}
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.CLASS)
public @interface ArgConfig {

	enum BuilderType {

		/**
		 * Fluent builder, this is the recommended builder for general usage<br>
		 * EX:<br>
		 * {@code a(1).b(2).c(3)}, where {@code a()} returns a class with only
		 * {@code b()} visible <br>
		 * This builder is the safe without runtime check as it implicitly
		 * forbid you from omitting mandatory args
		 *
		 */
		// TODO for next release
		// FLUENT(ReturnType.SUBCLASSED,
		// Check.NONE), /**
		// * Builder returns itself for chaining, no
		// * ordering is enforced, checked is done at
		// * runtime<br>
		// * EX:<br>
		// * {@code }a(2).b(2).a(1).c(3) } , notice that
		// * repeated args and unordered invocation are
		// * allowed<br>
		// * When a mandatory field is omitted, an
		// * {@link IllegalArgumentException} is thrown
		// */
		CHAINED_CHECKED(ReturnType.CHAINED,
				Check.RUNTIME), /**
								 * Builder setters returns itself, checking is
								 * done at runtime
								 */
		CHAINED_UNCHECKED(ReturnType.CHAINED,
				Check.NONE), /**
								 * Builder setters return void, behaves like
								 * {@link #CHAINED_CHECKED} but without chaining
								 */
		CHECKED(ReturnType.VOID,
				Check.RUNTIME), /**
								 * Builder returns void, behaves like
								 * {@link #CHAINED_UNCHECKED} but without
								 * chaining
								 */
		UNCHECKED(ReturnType.VOID, Check.NONE);

		final ReturnType returnType;
		final Check check;

		BuilderType(ReturnType returnType, Check check) {
			this.returnType = returnType;
			this.check = check;
		}

		enum ReturnType {
			CHAINED, SUBCLASSED, VOID
		}

		enum Check {
			RUNTIME, NONE
			// TODO compile time maybe? I guess we can get caller(traverse the
			// entire source tree) and then check for incomplete calls.... but
			// that's writing half a compiler ....
		}
	}

	/**
	 * Specifies what type of builder will be genrerated, see
	 * {@link ArgConfig.BuilderType} for all supported types
	 */
	BuilderType type() default BuilderType.CHAINED_CHECKED;

	@interface BuilderNamingRules {

		enum Type {
			ANDROID, UNDERSCORE, SIMPLE, TEMPLATE
		}

		Type value() default Type.SIMPLE;

		String splitWith() default "";

		String template();
	}

	interface MethodTransformation {

		String transform(String name);

	}

	/**
	 * Naming rules to be used while the builder class is generated, the rules
	 * are applied in order of declaration (array order) to each element.For
	 * example, to add a prefix to a field while keeping the camelCase,
	 * something like this can be used: <br>
	 * 1. Transform the first letter to upper case<br>
	 * and the second rule to append
	 *
	 */
	// BuilderNamingRules[]namingRules() default {};

	// TODO next version, no implementation yet
	// enum FieldNameConvention {
	// /**
	// * Fields prefixed with m for member (ex: mFoo, mBar, mFooBar)
	// */
	// ANDROID,
	//
	// /**
	// * Fields with no prefix (ex: foo, bar, fooBar)
	// */
	// SIMPLE
	// }
	//
	// /**
	// * Defines how field names are transformed when the builder is generated
	// */
	// FieldNameConvention convention() default FieldNameConvention.SIMPLE;

	/**
	 * Defines how builder method name are sorted
	 */
	enum Sort {
		CODE, INDEX, LEXICOGRAPHICAL, RANDOM
	}

	/**
	 * Used in conjunction with {@link Sort} to specify order
	 */
	enum Order {
		ASC, DSC
	}

	/**
	 * Defines the order of the generated builder methods
	 */
	Sort sort() default Sort.CODE;

	/**
	 * The order of the {@link #sort()}, defaults to {@link Order#ASC}
	 */
	Order order() default Order.ASC;

	Class<? extends ArgConcludingBuilder>concludingBuilder() default VoidBuilder.class;

	/**
	 * Whether the class should be processed; useful for debugging
	 */
	boolean enabled() default true;

}
