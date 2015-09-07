package com.sora.util.akatsuki.compiler.analyzers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.lang.model.type.TypeMirror;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.escape.CharEscaperBuilder;
import com.google.common.escape.Escaper;
import com.sora.util.akatsuki.compiler.BundleContext;
import com.sora.util.akatsuki.compiler.Log;
import com.sora.util.akatsuki.compiler.MustacheUtils;
import com.sora.util.akatsuki.compiler.ProcessorContext;
import com.sora.util.akatsuki.compiler.TransformationContext;
import com.sora.util.akatsuki.compiler.analyzers.CascadingTypeAnalyzer.Analysis;

public abstract class CascadingTypeAnalyzer<S extends CascadingTypeAnalyzer<S, T, A>, T extends TypeMirror, A extends Analysis>
		extends TransformationContext {

	private int cascadeDepth;

	public enum InvocationType {
		SAVE, RESTORE
	}

	public static final Escaper ESCAPER = new CharEscaperBuilder()
			.addEscapes(new char[] { '[', ']', '.', '(', ')' }, "_").toEscaper();

	protected TypeMirror targetMirror;
	protected TypeCastStrategy strategy = TypeCastStrategy.AUTO_CAST;
	protected String suffix = "";

	S target(TypeMirror mirror) {
		final S instance = createInstance(this);
		cloneFields(instance);
		instance.targetMirror = mirror;
		return instance;
	}

	S cast(TypeCastStrategy strategy) {
		final S instance = createInstance(this);
		cloneFields(instance);
		instance.strategy = strategy;
		return instance;
	}

	S suffix(String suffix) {
		final S instance = createInstance(this);
		cloneFields(instance);
		instance.suffix = this.suffix + suffix;
		return instance;
	}

	private void cloneFields(CascadingTypeAnalyzer<?, ?, ?> target) {
		target.targetMirror = this.targetMirror;
		target.suffix = this.suffix;
		target.strategy = this.strategy;
	}

	protected abstract S createInstance(TransformationContext context);

	public enum TypeCastStrategy {
		NO_CAST((context, from, to) -> null), //
		FORCE_CAST((context, from, to) -> to.toString()), //
		AUTO_CAST((context, from, to) -> context.utils().isAssignable(from, to, true) ? null
				: to.toString());//

		private final ClassCastFunction function;

		TypeCastStrategy(ClassCastFunction function) {
			this.function = function;
		}

		public interface ClassCastFunction {
			String createCastExpression(ProcessorContext context, TypeMirror from, TypeMirror to);
		}

	}

	public CascadingTypeAnalyzer(TransformationContext context) {
		super(context);
		this.cascadeDepth = 0;
	}

	public static class InvocationContext<T extends TypeMirror> {
		public final BundleContext bundleContext;
		public final Element<T> field;
		public final InvocationType type;

		public InvocationContext(BundleContext bundleContext, Element<T> field,
				InvocationType type) {
			this.bundleContext = bundleContext;
			this.field = field;
			this.type = type;
		}
	}

	protected abstract A createAnalysis(InvocationContext<T> context) throws UnknownTypeException;

	protected String fieldAccessor(InvocationContext<?> context) {
		return context.field.accessor(fn -> {
			String objectName = context.bundleContext.sourceObjectName();
			return Strings.isNullOrEmpty(objectName) ? fn : objectName + "." + fn;
		});
	}

	@SuppressWarnings("unchecked")
	public A transform(BundleContext bundleContext, Element<?> element, InvocationType type)
			throws UnknownTypeException {
		Log.verbose(this,
				type + ">" + cascadeDepth + Strings.repeat(" ", cascadeDepth) + "\\Cascade:"
						+ toString() + " -> " + element.toString() + " with " + bundleContext);
		return createAnalysis(new InvocationContext<>(bundleContext, (Element<T>) element, type));
	}

	protected Analysis cascade(CascadingTypeAnalyzer<?, ?, ?> transformation,
			InvocationContext<?> context, TypeMirror mirror) throws UnknownTypeException {
		return cascade(transformation, context, f -> f.refine(mirror));
	}

	protected Analysis cascade(CascadingTypeAnalyzer<?, ?, ?> transformation,
			InvocationContext<?> context, Function<Element<?>, Element<?>> elementTransformation)
					throws UnknownTypeException {
		transformation.cascadeDepth = cascadeDepth + 1;
		return transformation.transform(context.bundleContext,
				elementTransformation.apply(context.field), context.type);

	}

	protected TypeMirror targetOrElse(TypeMirror mirror) {
		return targetMirror != null ? targetMirror : mirror;
	}

	public interface Analysis {

		void prependOnce(Analysis analysis);

		void appendOnce(Analysis analysis);

		void prependOnce(String string);

		void appendOnce(String string);

		String emit();

		void transform(CodeTransform transform);

		void wrap(CodeTransform transformation);

		String preEmitOnce();

		String postEmitOnce();

	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("targetMirror", targetMirror)
				.add("strategy", strategy).add("suffix", suffix).toString();
	}

	static abstract class ChainedAnalysis implements Analysis {

		private final List<String> preEmit = new ArrayList<>();
		private final List<String> postEmit = new ArrayList<>();
		protected final CodeTransforms transforms = new CodeTransforms();

		@Override
		public void prependOnce(Analysis analysis) {
			appendAnalysis(preEmit, analysis);
		}

		@Override

		public void appendOnce(Analysis analysis) {
			appendAnalysis(postEmit, analysis);

		}

		@Override
		public void wrap(CodeTransform transform) {
			transforms.append(transform);
		}

		@Override
		public void prependOnce(String string) {
			preEmit.add(string);
		}

		@Override
		public void appendOnce(String string) {
			postEmit.add(string);
		}

		@Override
		public String preEmitOnce() {
			return preEmit.stream().collect(Collectors.joining());
		}

		@Override
		public String postEmitOnce() {
			return postEmit.stream().collect(Collectors.joining());
		}

		private void appendAnalysis(List<String> list, Analysis analysis) {
			list.add(analysis.preEmitOnce());
			list.add(analysis.emit());
			list.add(analysis.postEmitOnce());
		}

	}

	static class DefaultAnalysis extends ChainedAnalysis {

		private final Map<String, Object> scope;
		private RawStatement expression;

		DefaultAnalysis(Map<String, Object> scope, RawStatement statement) {
			this.scope = scope;
			this.expression = statement;
		}

		@Override
		public String emit() {
			return MustacheUtils.render(scope, transforms.apply(expression.render(scope)));
		}

		@Override
		public void transform(CodeTransform transform) {
			expression.transform(transform);
		}

		static <T extends TypeMirror> DefaultAnalysis of(CascadingTypeAnalyzer<?, T, ?> analyzer,
				RawStatement statement, InvocationContext<T> context,
				Map<String, Object> extraScope) {
			final HashMap<String, Object> scope = new HashMap<>();
			scope.put("fieldName", analyzer.fieldAccessor(context));
			scope.put("keyName", context.field.keyName());
			scope.put("bundle", context.bundleContext.bundleObjectName());
			if (extraScope != null)
				scope.putAll(extraScope);
			return new DefaultAnalysis(scope, statement);
		}

		static <T extends TypeMirror> DefaultAnalysis of(CascadingTypeAnalyzer<?, T, ?> analyzer,
				String methodName, InvocationContext<T> context) {
			final HashMap<String, Object> scope = new HashMap<>();
			scope.put("methodName", methodName);
			RawStatement statement;
			if (context.type == InvocationType.SAVE) {
				// field mirror -> target mirror , we don't need to cast
				statement = new InvocationStatement(
						"{{bundle}}.put{{methodName}}({{keyName}}, {{fieldName}})");
			} else {
				if (analyzer.targetMirror != null) {
					// when restore, target mirror is the return mirror so we
					// cast in opposite detection: target mirror -> field mirror
					final String castExpression = analyzer.strategy.function.createCastExpression(
							analyzer, analyzer.targetMirror, context.field.fieldMirror());
					if (!Strings.isNullOrEmpty(castExpression)) {
						scope.put("cast", true);
						scope.put("castExpression", castExpression);
					}
				}
				statement = new InvocationAssignmentStatement("{{fieldName}}",
						"{{#cast}}({{castExpression}}){{/cast}}{{bundle}}.get{{methodName}}({{keyName}})");
			}
			return of(analyzer, statement, context, scope);
		}

	}

	public static class ConversionException extends RuntimeException {

		public ConversionException(String message) {
			super(message);
		}

		public ConversionException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	public static class UnknownTypeException extends ConversionException {

		// public final Field<?> field;

		public UnknownTypeException(Element<?> element) {
			super("unknown type " + element + "(" + element.refinedMirror().getClass() + ")");
		}

		public UnknownTypeException(TypeMirror mirror) {
			super("unknown type " + mirror + "(" + mirror.getClass() + ")");
		}

	}

	@FunctionalInterface
	public interface CodeTransform extends Function<String, String> {

	}

	public static class CodeTransforms {
		private final ArrayList<CodeTransform> transforms = new ArrayList<>();

		public void append(CodeTransform transform) {
			this.transforms.add(transform);
		}

		public String apply(String source) {
			for (CodeTransform transform : transforms) {
				source = transform.apply(source);
			}
			return source;
		}
	}

	public interface RawStatement {

		String render(Object scope);

		void transform(CodeTransform transformation);

	}

	public static class InvocationStatement implements RawStatement {

		private String template;

		public InvocationStatement(String template) {
			this.template = template;
		}

		@Override
		public String render(Object scope) {
			return MustacheUtils.render(scope, template) + ";\n";
		}

		@Override
		public void transform(CodeTransform transform) {
			template = transform.apply(template);
		}
	}

	public static class InvocationAssignmentStatement extends InvocationStatement {

		private final String variable;

		public InvocationAssignmentStatement(String variable, String template) {
			super(template);
			this.variable = variable;
		}

		@Override
		public String render(Object scope) {
			return MustacheUtils.render(scope, variable) + " = " + super.render(scope);
		}
	}
}
