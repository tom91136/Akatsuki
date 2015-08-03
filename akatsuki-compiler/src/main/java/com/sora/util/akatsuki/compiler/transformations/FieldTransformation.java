package com.sora.util.akatsuki.compiler.transformations;

import com.sora.util.akatsuki.compiler.BundleContext;
import com.sora.util.akatsuki.compiler.BundleRetainerModel.Field;
import com.sora.util.akatsuki.compiler.InvocationSpec.InvocationType;
import com.sora.util.akatsuki.compiler.MustacheUtils;
import com.sora.util.akatsuki.compiler.ProcessorContext;
import com.sora.util.akatsuki.compiler.ProcessorUtils;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.processing.Messager;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Project: Akatsuki Created by Tom on 7/23/2015.
 */
public abstract class FieldTransformation<T extends TypeMirror> implements ProcessorContext {

	private final ProcessorContext context;

	public FieldTransformation(ProcessorContext context) {
		this.context = context;
	}

	public static class InvocationContext<T extends TypeMirror> {
		public final BundleContext bundleContext;
		public final Field<T> field;
		public final InvocationType type;

		public InvocationContext(BundleContext bundleContext, Field<T> field, InvocationType type) {
			this.bundleContext = bundleContext;
			this.field = field;
			this.type = type;
		}
	}

	protected abstract Invocation createInvocation(InvocationContext<T> context)
			throws UnknownTypeException;

	@SuppressWarnings("unchecked")
	public Invocation transform(BundleContext bundleContext, Field<?> field, InvocationType type)
			throws UnknownTypeException {
		return createInvocation(new InvocationContext<>(bundleContext, (Field<T>) field, type));
	}

	public Invocation cascade(FieldTransformation<?> strategy, InvocationContext<?> context,
			TypeMirror mirror) throws UnknownTypeException {
		return strategy.transform(context.bundleContext, context.field.refine(mirror),
				context.type);
	}

	@Override
	public Types types() {
		return context.types();
	}

	@Override
	public Elements elements() {
		return context.elements();
	}

	@Override
	public ProcessorUtils utils() {
		return context.utils();
	}

	@Override
	public Messager messager() {
		return context.messager();
	}

	public interface Invocation {

		String create();

	}

	static class MustacheTemplateSupplier implements Invocation {

		private final Map<String, Object> scope;
		private final String template;

		public MustacheTemplateSupplier(Map<String, Object> scope, String template) {
			this.scope = scope;
			this.template = template;
		}

		@Override
		public String create() {
			return MustacheUtils.render(scope, template);
		}

		static Map<String, Object> createScope(Field<?> field, BundleContext bundleContext) {
			final HashMap<String, Object> map = new HashMap<>();
			map.put("fieldName", bundleContext.sourceObjectName() + "." + field.fieldName());
			map.put("keyName", field.uniqueName());
			map.put("bundle", bundleContext.bundleObjectName());
			return map;
		}

		static MustacheTemplateSupplier withMethodName(BundleContext context,
				ProcessorContext processorContext, Field<?> field, CharSequence methodName,
				InvocationType type) {
			return withMethodNameAndCast(context, processorContext, field, null, methodName, type);
		}

		static MustacheTemplateSupplier withMethodNamePossibleCast(BundleContext context,
				ProcessorContext processorContext, Field<?> field, TypeMirror methodMirror,
				CharSequence methodName, InvocationType type) {
			TypeMirror castType = processorContext.utils().isSameType(field.fieldMirror(),
					methodMirror, true) ? null : field.fieldMirror();
			return withMethodNameAndCast(context, processorContext, field, castType, methodName,
					type);
		}

		static MustacheTemplateSupplier withMethodNameAndCast(BundleContext context,
				ProcessorContext processorContext, Field<?> field, TypeMirror castType,
				CharSequence methodName, InvocationType type) {
			final Map<String, Object> scope = createScope(field, context);
			String template;
			if (type == InvocationType.SAVE) {
				template = "{{bundle}}.put{{methodName}}(\"{{keyName}}\", {{fieldName}})";
			} else {
				if (castType != null) {
					scope.put("cast", true);
					scope.put("fieldType", castType);
				}
				template = "{{fieldName}}={{#cast}}({{fieldType}}){{/cast}}{{bundle}}"
						+ ".get{{methodName}}(\"{{keyName}}\")";
			}
			scope.put("methodName", methodName);
			return new MustacheTemplateSupplier(scope, template);
		}

	}

	public static class ConversionException extends Exception {

		public ConversionException(String message) {
			super(message);
		}

		public ConversionException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	public static class UnknownTypeException extends ConversionException {

		// public final Field<?> field;

		public UnknownTypeException(Field<?> field) {
			super("unknown type " + field + "(" + field.refinedMirror().getClass() + ")");
		}

		public UnknownTypeException(TypeMirror mirror) {
			super("unknown type " + mirror + "(" + mirror.getClass() + ")");
		}

	}
}
