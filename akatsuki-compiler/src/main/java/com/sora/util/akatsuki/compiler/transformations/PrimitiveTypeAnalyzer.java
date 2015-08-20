package com.sora.util.akatsuki.compiler.transformations;

import com.sora.util.akatsuki.compiler.transformations.CascadingTypeAnalyzer.DefaultAnalysis;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;

import static com.sora.util.akatsuki.compiler.Utils.toCapitalCase;

public class PrimitiveTypeAnalyzer
		extends CascadingTypeAnalyzer<PrimitiveTypeAnalyzer, TypeMirror, DefaultAnalysis> {

	public enum Type {
		BOXED, UNBOXED, FOLLOW
	}

	private final Type type;

	public PrimitiveTypeAnalyzer(TransformationContext context, Type type) {
		super(context);
		this.type = type == null ? Type.UNBOXED : type;
	}

	@Override
	protected PrimitiveTypeAnalyzer createInstance(TransformationContext context) {
		return new PrimitiveTypeAnalyzer(context, type);
	}

	@Override
	public DefaultAnalysis createAnalysis(InvocationContext<TypeMirror> context)
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
		String methodName = (suffix != null) ? (typeName.toString() + suffix) : typeName.toString();
		return DefaultAnalysis.of(this, methodName, context);
	}
}
