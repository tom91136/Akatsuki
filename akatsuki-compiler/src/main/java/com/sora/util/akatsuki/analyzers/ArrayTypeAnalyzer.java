package com.sora.util.akatsuki.analyzers;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;

import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.sora.util.akatsuki.AndroidTypes;
import com.sora.util.akatsuki.MustacheUtils;
import com.sora.util.akatsuki.TransformationContext;
import com.sora.util.akatsuki.analyzers.CascadingTypeAnalyzer.Analysis;
import com.sora.util.akatsuki.analyzers.Element.Builder.SetterMode;
import com.sora.util.akatsuki.analyzers.PrimitiveTypeAnalyzer.Type;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.CodeBlock.Builder;

public class ArrayTypeAnalyzer
		extends CascadingTypeAnalyzer<ArrayTypeAnalyzer, ArrayType, Analysis> {

	static final AndroidTypes[] SUPPORTED_ARRAY_TYPES = { AndroidTypes.Parcelable,
			AndroidTypes.CharSequence, AndroidTypes.String };

	// for nested arrays (multidimensional arrays will have the same type)
	private final int depth;
	private static final String LOOP_EXPRESSION = "for (int $L = $L; $L < $L; $L++)";

	@Override
	protected ArrayTypeAnalyzer createInstance(TransformationContext context) {
		return new ArrayTypeAnalyzer(context, depth);
	}

	public ArrayTypeAnalyzer(TransformationContext context) {
		super(context);
		this.depth = 0;
	}

	private ArrayTypeAnalyzer(TransformationContext context, int depth) {
		super(context);
		this.depth = depth;
	}

	@Override
	public Analysis createAnalysis(InvocationContext<ArrayType> context)
			throws UnknownTypeException {
		final TypeMirror component = context.field.refinedMirror().getComponentType();

		// bundle supports all primitives
		if (utils().isPrimitive(component)) {
			return cascade(new PrimitiveTypeAnalyzer(this, Type.UNBOXED).suffix("Array"), context,
					component);
		}

		// bundle also supports some built in types
		final Optional<AndroidTypes> found = Arrays.stream(SUPPORTED_ARRAY_TYPES).sorted()
				.filter(t -> utils().isAssignable(component, utils().of(t.className), true))
				.findFirst();

		if (found.isPresent()) {
			System.out.println(component);
			return cascade(new ObjectTypeAnalyzer(this).suffix("Array")
					.target(types().getArrayType(found.get().asMirror(this))), context, component);
		} else {
			// everything else
			final Element<TypeMirror> refined = context.field.refine(component);

			CascadingTypeAnalyzer<?, ? extends TypeMirror, ? extends Analysis> resolved;
			if (component.getKind() == TypeKind.ARRAY) {
				throw new ConversionException(
						context.field + " is a multidimentional array, which is not supported "
								+ "(open an issue on Github if you want it)");
				// multidimensional array, we need to supply depth
				// resolved = new ArrayTypeAnalyzer(this, depth + 1);
			} else {
				// TODO, never really happens, handled by found type above
				resolved = resolve(refined).cast(TypeCastStrategy.NO_CAST);
			}

			// give up
			if (resolved == null)
				throw new UnknownTypeException(context.field);

			final String accessor = fieldAccessor(context);

			String currentCounter = counterName(context, depth);

			final Analysis cascade = cascade(resolved, context, f -> {
				return f.toBuilder().type(component)
						.keyName(SetterMode.APPEND, " + \"_\"+" + currentCounter)
						.accessor(SetterMode.APPEND, "[" + currentCounter + "]").build();
			});

			final HashMap<String, Object> scope = new HashMap<>();
			scope.put("bundle", context.bundleContext.bundleObjectName());
			scope.put("accessor", accessor + ".length");
			scope.put("lengthKey", "\"length_" + currentCounter + "_r\""
					+ (depth == 0 ? "" : " + " + counterName(context, depth - 1)));
			scope.put("lengthVar", "length_" + currentCounter);

			final String lengthStatement = MustacheUtils.render(scope,
					context.type == InvocationType.SAVE
							? "{{bundle}}.putInt({{lengthKey}}, {{accessor}})"
							: "int {{lengthVar}} = {{bundle}}.getInt({{lengthKey}})");

			cascade.wrap(original -> {
				final Builder builder = CodeBlock.builder();
				if (context.type == InvocationType.SAVE) {
					builder.beginControlFlow("if($L != null)", accessor)
							.addStatement(lengthStatement)
							.beginControlFlow(LOOP_EXPRESSION, currentCounter, 0, currentCounter,
									accessor + ".length", currentCounter)
							.add(original).endControlFlow().endControlFlow();
				} else {

					builder.addStatement(lengthStatement)
							.beginControlFlow(LOOP_EXPRESSION, currentCounter, 0, currentCounter,
									scope.get("lengthVar"), currentCounter)
							.add(original).endControlFlow();

				}
				return builder.build().toString();
			});

			return cascade;
		}

	}

	private String counterName(InvocationContext<?> context, int depth) {
		return context.field.uniqueName() + "_" + depth;
	}

}
