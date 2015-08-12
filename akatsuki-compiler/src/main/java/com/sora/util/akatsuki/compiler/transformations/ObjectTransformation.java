package com.sora.util.akatsuki.compiler.transformations;

import com.sora.util.akatsuki.compiler.AndroidTypes;

import java.util.Arrays;

import javax.lang.model.type.DeclaredType;

/**
 * Project: Akatsuki Created by Tom on 7/24/2015.
 */
public class ObjectTransformation extends SuffixedTransformation<DeclaredType> {

	public ObjectTransformation(TransformationContext context) {
		super(context);
	}

	@Override
	public Invocation createInvocation(InvocationContext<DeclaredType> context)
			throws UnknownTypeException {

		// handle SparseArray<?>
		if (utils().isSameType(context.field.fieldMirror(),
				utils().of(AndroidTypes.SparseArray.className), true)) {

			final DeclaredType sparseArrayMirror = (DeclaredType) context.field.fieldMirror();

			if (utils().isAssignable(sparseArrayMirror.getTypeArguments().get(0),
					utils().of(AndroidTypes.Parcelable.className), true)) {
				// ? extends Parcelable
				return MustacheTemplateSupplier.withMethodName(context.bundleContext, this,
						context.field, "SparseParcelableArray", context.type);
			} else {
				// ? extends Object
				return null;
			}
		}

		final AndroidTypes found = Arrays.stream(AndroidTypes.values())
				.filter(t -> utils().isAssignable(context.field.refinedMirror(),
						utils().of(t.className), true))
				.findFirst().orElseThrow(() -> new UnknownTypeException(context.field));

		String methodName = found.typeAlias != null ? found.typeAlias.toString()
				: found.asMirror(this).asElement().getSimpleName().toString();

		if (suffix != null)
			methodName += suffix;

		// we use field mirror here because we don't want to screw up other
		// types the cascades to this type
		if (utils().isAssignable(context.field.fieldMirror(),
				utils().of(AndroidTypes.Parcelable.className), true)) {
			// parcelable has a getter of <T> T getParcelable(String) so no
			// casting is needed
			return MustacheTemplateSupplier.withMethodName(context.bundleContext, this,
					context.field, methodName, context.type);
		}

		// TODO clean up, this whole MustacheTemplateSupplier looks shitty and
		// redundant
		if (forceCast) {
			return MustacheTemplateSupplier.withMethodNameAndCast(context.bundleContext, this,
					context.field, methodMirror, methodName, context.type);
		} else {
			return MustacheTemplateSupplier.withMethodNamePossibleCast(context.bundleContext, this,
					context.field, methodMirror != null ? methodMirror : found.asMirror(this),
					methodName, context.type);
		}

	}
}
