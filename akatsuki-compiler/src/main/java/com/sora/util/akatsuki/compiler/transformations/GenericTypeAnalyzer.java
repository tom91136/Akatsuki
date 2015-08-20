package com.sora.util.akatsuki.compiler.transformations;

import com.sora.util.akatsuki.compiler.transformations.CascadingTypeAnalyzer.Analysis;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

public class GenericTypeAnalyzer
		extends CascadingTypeAnalyzer<GenericTypeAnalyzer, TypeMirror, Analysis> {

	private TypeMirror resolvedMirror;

	public GenericTypeAnalyzer(TransformationContext context) {
		super(context);

	}

	@Override
	protected GenericTypeAnalyzer createInstance(TransformationContext context) {
		return new GenericTypeAnalyzer(context);
	}

	@Override
	protected Analysis createAnalysis(InvocationContext<TypeMirror> context)
			throws UnknownTypeException {

		TypeVariable typeVariable = (TypeVariable) context.field.refinedMirror();
		final TypeMirror upperBound = typeVariable.getUpperBound();
		CascadingTypeAnalyzer<?, ?, ?> transformation = null;
		if (upperBound instanceof DeclaredType) {
			// we have a concrete type, good
			resolvedMirror = upperBound;
			transformation = resolve(context.field.refine(resolvedMirror));
		} else if (upperBound instanceof IntersectionType) {
			for (TypeMirror bound : ((IntersectionType) upperBound).getBounds()) {
				// as long as the first bound matches anything, we use it
				// TODO some bounds should have priority such as Parcelable and
				// serializable. but how do we decide?
				final CascadingTypeAnalyzer<?, ?, ?> found = resolve(
						context.field.refine(resolvedMirror));
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

		if (resolvedMirror == null || transformation == null)
			throw new UnknownTypeException(context.field);

		return cascade(transformation.target(resolvedMirror).cast(TypeCastStrategy.AUTO_CAST),
				context, resolvedMirror);
	}
}
