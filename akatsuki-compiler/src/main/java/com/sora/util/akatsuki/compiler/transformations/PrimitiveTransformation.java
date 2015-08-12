package com.sora.util.akatsuki.compiler.transformations;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;

import static com.sora.util.akatsuki.compiler.Utils.toCapitalCase;

/**
 * Project: Akatsuki Created by Tom on 7/24/2015.
 */
public class PrimitiveTransformation extends SuffixedTransformation<TypeMirror> {

	enum Type {
		BOXED, UNBOXED, FOLLOW
	}

	private final Type type;

	public PrimitiveTransformation(TransformationContext context, Type type) {
		super(context);
		this.type = type;
	}

	public PrimitiveTransformation(TransformationContext context) {
		super(context);
		this.type = Type.UNBOXED;
	}

	@Override
	public Invocation createInvocation(InvocationContext<TypeMirror> context)
			throws UnknownTypeException {
		// boxed primitives have different names (at least for int)
		CharSequence typeName;
		final TypeMirror refinedMirror = context.field.refinedMirror();
		if (refinedMirror instanceof DeclaredType) {
			// we are boxed
			typeName = this.type == Type.UNBOXED
					? toCapitalCase(types().unboxedType(refinedMirror).getKind().name())
					: ((DeclaredType) refinedMirror).asElement().getSimpleName();
		} else {
			// we are unboxed
			typeName = this.type == Type.BOXED
					? types().boxedClass((PrimitiveType) refinedMirror).getSimpleName()
					: toCapitalCase(refinedMirror.getKind().name());
		}
		String methodName = (suffix != null) ? (typeName.toString() + suffix.toString())
				: typeName.toString();

		if (methodMirror == null) {
			// no casting needed
			return MustacheTemplateSupplier.withMethodName(context.bundleContext, this,
					context.field, methodName, context.type);
		} else {
			return MustacheTemplateSupplier.withMethodNameAndCast(context.bundleContext, this,
					context.field, methodMirror, methodName, context.type);
		}

	}
}
