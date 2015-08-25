package com.sora.util.akatsuki.compiler.transformations;

import com.sora.util.akatsuki.compiler.AndroidTypes;
import com.sora.util.akatsuki.compiler.ProcessorElement;
import com.sora.util.akatsuki.compiler.transformations.CascadingTypeAnalyzer.Analysis;
import com.sora.util.akatsuki.compiler.transformations.PrimitiveTypeAnalyzer.Type;
import com.squareup.javapoet.CodeBlock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

public class CollectionTypeAnalyzer
		extends CascadingTypeAnalyzer<CollectionTypeAnalyzer, DeclaredType, Analysis> {

	static final AndroidTypes[] SUPPORTED_ARRAY_LIST_TYPES = { AndroidTypes.CharSequence,
			AndroidTypes.String };

	static final InstantiationStatement COPY_CONSTRUCTOR = (analyzer, type, source) -> CodeBlock
			.builder().add("new $L($L)", type.toString().replace("<E>", "<>"), source).build()
			.toString();

	interface InstantiationStatement {
		String createStatement(CollectionTypeAnalyzer analyzer, TypeMirror type, String source);
	}

	public CollectionTypeAnalyzer(TransformationContext context) {
		super(context);
	}

	@Override
	protected CollectionTypeAnalyzer createInstance(TransformationContext context) {
		return new CollectionTypeAnalyzer(context);
	}

	@Override
	public Analysis createAnalysis(InvocationContext<DeclaredType> context)
			throws UnknownTypeException {

		// ArrayList is supported
		final Optional<TypeMirror> arrayListType = getSupportedArrayListType(context.field);
		final DeclaredType rawTypeMirror = context.field.refinedMirror();
		if (arrayListType.isPresent()) {
			final TypeMirror mirror = arrayListType.get();
			final Analysis analysis;

			Function<ProcessorElement<?>, ProcessorElement<?>> ARRAY_LIST_WRAPPER = (e) -> {
				// if our list is not an ArrayList, wrap it in one
				if (context.type == InvocationType.SAVE
						&& !utils().isSameType(rawTypeMirror, true, utils().of(ArrayList.class))) {
					e = e.toBuilder().fieldNameTransforms(o -> COPY_CONSTRUCTOR
							.createStatement(this, utils().of(ArrayList.class), o)).build();
				}
				return e;
			};

			CascadingTypeAnalyzer<?, ?, ?> analyzer;

			if (utils().isPrimitive(mirror)) {
				analyzer = new PrimitiveTypeAnalyzer(this, Type.BOXED).suffix("ArrayList");
			} else {
				final DeclaredType methodMirror = utils()
						.getDeclaredType((DeclaredType) utils().of(ArrayList.class), mirror);
				analyzer = new ObjectTypeAnalyzer(this).suffix("ArrayList").target(methodMirror);
			}

			analysis = cascade(analyzer.cast(TypeCastStrategy.NO_CAST), context,
					e -> ARRAY_LIST_WRAPPER.apply(e.refine(mirror)));

			if (context.type == InvocationType.RESTORE) {
				analysis.transform(original -> {
					if (utils().isSameType(rawTypeMirror, true, utils().of(ArrayList.class))) {
						// ArrayList is natively supported; we can't possibly
						// know
						// the type of the List at compile time :(
						// TODO do some runtime checking for the interface List
						// ?
						return original;
					}
					if (!utils().isSameType(rawTypeMirror, true, utils().of(List.class))) {
						if (utils().isSameType(rawTypeMirror, true, utils().of(LinkedList.class),
								utils().of(CopyOnWriteArrayList.class))) {
							return COPY_CONSTRUCTOR.createStatement(this, rawTypeMirror, original);
						}
						throw new UnknownTypeException(context.field);
					}
					return original;
				});
			}

			return analysis;
		} else {

			final List<? extends TypeMirror> mirror = rawTypeMirror.getTypeArguments();
			if (mirror.size() != 1) {
				// collections only have one generic parameter
				throw new UnknownTypeException(context.field);
			}

			final TypeMirror typeMirror = mirror.get(0);

			final Analysis analysis = cascade(resolve(context.field.refine(typeMirror)), context,
					typeMirror);
			if(analysis == null)
				throw new UnknownTypeException(context.field);

			throw new UnsupportedOperationException("not implemented(yet?), use Parceler instead");

			// return new Invocation() {
			// @Override
			// public String create() {
			// return CodeBlock.builder()
			// .beginControlFlow("for ()", from, to)
			// .addStatement(invocation.create()).endControlFlow().build().toString();
			// }
			// };
		}
	}

	private Optional<TypeMirror> getSupportedArrayListType(ProcessorElement<DeclaredType> field) {
		if (utils().isAssignable(field.refinedMirror(), utils().of(List.class), true)) {
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
