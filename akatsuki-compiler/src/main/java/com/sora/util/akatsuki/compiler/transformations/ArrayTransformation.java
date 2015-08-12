package com.sora.util.akatsuki.compiler.transformations;

import com.sora.util.akatsuki.compiler.AndroidTypes;

import java.util.Arrays;
import java.util.Optional;

import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeMirror;

/**
 * Project: Akatsuki Created by Tom on 7/24/2015.
 */
public class ArrayTransformation extends FieldTransformation<ArrayType> {

	static final AndroidTypes[] SUPPORTED_ARRAY_TYPES = { AndroidTypes.Parcelable,
			AndroidTypes.CharSequence, AndroidTypes.String };

	public ArrayTransformation(TransformationContext context) {
		super(context);
	}

	@Override
	public Invocation createInvocation(InvocationContext<ArrayType> context)
			throws UnknownTypeException {
		final TypeMirror component = context.field.refinedMirror().getComponentType();

		// bundle supports all primitives
		if (utils().isPrimitive(component)) {
			return cascade(new PrimitiveTransformation(this).withSuffix("Array"), context,
					component);
		}

		// bundle also supports some built in types
		final Optional<AndroidTypes> found = Arrays.stream(SUPPORTED_ARRAY_TYPES).sorted()
				.filter(t -> utils().isAssignable(component, utils().of(t.className), true))
				.findFirst();

		if (found.isPresent()) {
			return cascade(new ObjectTransformation(this).withSuffix("Array").withPossibleCast(
					types().getArrayType(found.get().asMirror(this))), context, component);
		} else {
			// everything else
			throw new UnknownTypeException(context.field);
		}
	}

}
