package com.sora.util.akatsuki.compiler.transformations;

import com.sora.util.akatsuki.compiler.BundleRetainerModel.Field;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

public class GenericTransformation extends FieldTransformation<TypeMirror> {

	private TypeMirror resolvedMirror;
	private FieldTransformation<? extends TypeMirror> transformation;

	public GenericTransformation(TransformationContext context, Field<?> field) {
		super(context);
		TypeVariable typeVariable = (TypeVariable) field.refinedMirror();
		final TypeMirror upperBound = typeVariable.getUpperBound();
		if (upperBound instanceof DeclaredType) {
			// we have a concrete type, good
			resolvedMirror = upperBound;
			transformation = resolve(field.refine(resolvedMirror));
		} else if (upperBound instanceof IntersectionType) {
			for (TypeMirror bound : ((IntersectionType) upperBound).getBounds()) {
				// as long as the first bound matches anything, we use it
				// TODO some bounds should have priority such as Parcelable and
				// serializable. but how do we decide?
				final FieldTransformation<? extends TypeMirror> found = resolve(
						field.refine(resolvedMirror));
				if (found != null) {
					// we can probably do the following analysis to make this
					// better:
					// 1. Iterate through all strategies and save them as a list
					// 2. Further filter out the working ones (ones that does
					// not
					// throw)
					// 3. Do a super interface check on each of them and see
					// which
					// one is the most suitable
					resolvedMirror = bound;
					transformation = found;
					break;
				}
			}
		}
	}

	@Override
	protected Invocation createInvocation(InvocationContext<TypeMirror> context)
			throws UnknownTypeException {
		if (resolvedMirror == null || transformation == null)
			throw new UnknownTypeException(context.field);
		if (transformation instanceof SuffixedTransformation) {
			transformation = ((SuffixedTransformation<?>) transformation)
					.withForcedCast(resolvedMirror);
		}
		return cascade(transformation, context, resolvedMirror);
	}
}
