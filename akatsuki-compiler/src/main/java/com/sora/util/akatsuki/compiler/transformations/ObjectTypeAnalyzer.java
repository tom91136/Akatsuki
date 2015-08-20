package com.sora.util.akatsuki.compiler.transformations;

import com.sora.util.akatsuki.compiler.AndroidTypes;
import com.sora.util.akatsuki.compiler.transformations.CascadingTypeAnalyzer.DefaultAnalysis;

import java.util.Arrays;

import javax.lang.model.type.DeclaredType;

public class ObjectTypeAnalyzer
		extends CascadingTypeAnalyzer<ObjectTypeAnalyzer, DeclaredType, DefaultAnalysis> {

	public ObjectTypeAnalyzer(TransformationContext context) {
		super(context);
	}

	@Override
	protected ObjectTypeAnalyzer createInstance(TransformationContext context) {
		return new ObjectTypeAnalyzer(context);
	}

	@Override
	public DefaultAnalysis createAnalysis(InvocationContext<DeclaredType> context)
			throws UnknownTypeException {

		// handle SparseArray
		if (utils().isSameType(context.field.fieldMirror(),
				utils().of(AndroidTypes.SparseArray.className), true)) {

			final DeclaredType sparseArrayMirror = (DeclaredType) context.field.fieldMirror();

			if (utils().isAssignable(sparseArrayMirror.getTypeArguments().get(0),
					utils().of(AndroidTypes.Parcelable.className), true)) {
				// SparseArray<? extends Parcelable>

				return DefaultAnalysis.of(this, "SparseParcelableArray", context);

			} else {
				// SparseArray<? extends Object>
				return null;
			}
		}

		final AndroidTypes found = Arrays.stream(AndroidTypes.values())
				.filter(t -> utils().isAssignable(context.field.refinedMirror(),
						utils().of(t.className), true))
				.findFirst().orElseThrow(() -> new UnknownTypeException(context.field));

		String methodName = found.typeAlias != null ? found.typeAlias.toString()
				: found.asMirror(this).asElement().getSimpleName().toString();
		methodName += suffix;
		// we use field mirror here because we don't want to screw up other
		// types the cascades to this type
		if (utils().isAssignable(context.field.fieldMirror(),
				utils().of(AndroidTypes.Parcelable.className), true)) {
			// parcelable has a getter of <T> T getParcelable(String) so no
			// casting is needed
			// return cascade(target(null), Function.identity(), context);
			return DefaultAnalysis.of(cast(TypeCastStrategy.NO_CAST), methodName, context);
		}

		return DefaultAnalysis.of(target(targetOrElse(found.asMirror(this))), methodName, context);
	}
}
