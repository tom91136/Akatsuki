package com.sora.util.akatsuki.compiler.transformations;

import com.sora.util.akatsuki.compiler.AndroidTypes;
import com.sora.util.akatsuki.compiler.BundleRetainerModel.Field;
import com.sora.util.akatsuki.compiler.transformations.PrimitiveTransformation.Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * Project: Akatsuki Created by Tom on 7/24/2015.
 */
public class CollectionTransformation extends FieldTransformation<DeclaredType> {

	static final AndroidTypes[] SUPPORTED_ARRAY_LIST_TYPES = { AndroidTypes.CharSequence,
			AndroidTypes.String };

	public CollectionTransformation(TransformationContext context) {
		super(context);
	}

	@Override
	public Invocation createInvocation(InvocationContext<DeclaredType> context)
			throws UnknownTypeException {

		// ArrayList is supported
		final Optional<TypeMirror> arrayListType = getSupportedArrayListType(context.field);
		if (arrayListType.isPresent()) {
			final TypeMirror mirror = arrayListType.get();
			if (utils().isPrimitive(mirror)) {
				// Integer is final, not casting is needed
				return cascade(
						new PrimitiveTransformation(this, Type.BOXED).withSuffix("ArrayList"),
						context, mirror);
			} else {
				final DeclaredType methodMirror = utils()
						.getDeclaredType((DeclaredType) utils().of(ArrayList.class), mirror);
				return cascade(new ObjectTransformation(this).withSuffix("ArrayList")
						.withPossibleCast(methodMirror), context, mirror);
			}
		} else {

//			final List<? extends TypeMirror> mirror = context.field.refinedMirror()
//					.getTypeArguments();
//			if (mirror.size() != 1) {
//				// collections only have one generic parameter
//				throw new UnknownTypeException(context.field);
//			}
//
//			final TypeMirror typeMirror = mirror.get(0);
//
//			final Invocation invocation = cascade(resolve(context.field.refine(typeMirror)),
//					context, typeMirror);
//
//			return new Invocation() {
//				@Override
//				public String create() {
//					return CodeBlock.builder()
//							.beginControlFlow("for ()", from, to)
//							.addStatement(invocation.create()).endControlFlow().build().toString();
//				}
//			};
		}

		return null;
	}

	private Optional<TypeMirror> getSupportedArrayListType(Field<DeclaredType> field) {
		if (utils().isSameType(field.refinedMirror(), utils().of(ArrayList.class), true)) {
			final TypeMirror mirror = field.refinedMirror().getTypeArguments().get(0);

			final TypeMirror integerMirror = utils().of(Integer.class);
			final DeclaredType parcelableType = AndroidTypes.Parcelable.asMirror(this);
			// bundle supports array list of some types and primitive of integer
			// ONLY... why?
			if (utils().isSameType(mirror, integerMirror, true)) {
				// ArrayList<Integer>
				return Optional.of(integerMirror);
				// ArrayList<? extends Parcelable>
			} else if (utils().isAssignable(mirror, parcelableType, true)) {
				return Optional.of(parcelableType);
			} else {
				// ArrayList<SUPPORTED_TYPES>
				return Arrays.stream(SUPPORTED_ARRAY_LIST_TYPES).sorted()
						.filter(t -> utils().isSameType(mirror, utils().of(t.className), true))
						.findFirst().map(t -> t.asMirror(this));
			}

		}
		return Optional.empty();
	}
}
