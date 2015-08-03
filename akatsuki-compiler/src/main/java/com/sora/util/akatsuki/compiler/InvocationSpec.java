package com.sora.util.akatsuki.compiler;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.MustacheFactory;
import com.google.common.base.Joiner;
import com.sora.util.akatsuki.TransformationTemplate;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Project: Akatsuki Created by tom91136 on 15/07/2015.
 */
public abstract class InvocationSpec<T extends InvocationSpec<T>> {

	protected static final Joiner JOINER = Joiner.on("");
	protected static final Joiner COMMA_JOINER = Joiner.on(',');

	private ArrayList<CharSequence> appended = new ArrayList<>();
	private ArrayList<CharSequence> prepended = new ArrayList<>();
	protected final BundleContext context;
	protected final InvocationType type;
	protected final CharSequence fieldName;
	protected final List<CharSequence> methodNameSegments = new ArrayList<>();
	protected final List<CharSequence> parameters = new ArrayList<>();

	public enum InvocationType {
		SAVE, RESTORE
	}

	protected InvocationSpec(BundleContext context, InvocationType type, CharSequence fieldName) {
		this.context = context;
		this.type = type;
		this.fieldName = fieldName;

	String save = "{{bundle}}.{{prefix}}{{methodName}}(\"{{fieldName}}\", {{fieldName}});\n";



	}

	@SuppressWarnings("unchecked")
	protected T self() {
		return (T) this;
	}

	public T appendBlocks(CharSequence... blocks) {
		this.appended.addAll(Arrays.asList(blocks));
		return self();
	}

	public T prependBlocks(CharSequence... blocks) {
		this.prepended.addAll(Arrays.asList(blocks));
		return self();
	}

	public T appendMethodNames(CharSequence... names) {
		if (!invocationStatementMutable())
			throw new UnsupportedOperationException();
		this.methodNameSegments.addAll(Arrays.asList(names));
		return self();
	}

	public T appendParameters(CharSequence... parameters) {
		if (!invocationStatementMutable())
			throw new UnsupportedOperationException();
		this.parameters.addAll(Arrays.asList(parameters));
		return self();
	}

	protected abstract String createStatement(CharSequence prepended, CharSequence appended);

	public final String createInvocation() {
		return createStatement(JOINER.join(prepended), JOINER.join(appended));
	}

	public boolean invocationStatementMutable() {
		return true;
	}

	protected CharSequence createParameter() {
		return "(" + COMMA_JOINER.join(parameters) + ")";
	}

	protected CharSequence createMethodName() {
		return JOINER.join(methodNameSegments);
	}

	protected static CharSequence quoted(CharSequence parameter) {
		return "\"" + parameter + "\"";
	}

	static class BundleAccessorInvocationSpec extends InvocationSpec<BundleAccessorInvocationSpec> {

		// final List<CharSequence> methodNameSegments = new ArrayList<>();
		final CharSequence keyName;

		BundleAccessorInvocationSpec(BundleContext context, InvocationType type,
				CharSequence methodName, CharSequence keyName, CharSequence fieldName) {
			super(context, type, fieldName);
			this.keyName = keyName;
			appendMethodNames(methodName);
			appendParameters(quoted(keyName));
			if (type == InvocationType.SAVE)
				appendParameters(context.sourceObjectName() + "." + fieldName);

		}

		BundleAccessorInvocationSpec(BundleContext context, InvocationType type,
				CharSequence methodName, CharSequence fieldName) {
			this(context, type, methodName, fieldName, fieldName);
		}

		@Override
		protected String createStatement(CharSequence prepended, CharSequence appended) {
			String statement;
			if (type == InvocationType.RESTORE) {
				statement = (String.format("%s.%s = %s.get", context.sourceObjectName(), fieldName,
						context.bundleObjectName()));
			} else {
				statement = String.format("%s.put", context.bundleObjectName());
			}
			return prepended + statement + createMethodName() + createParameter() + ";\n"
					+ appended;
		}

	}

	static class TemplateInvocationSpec extends InvocationSpec<TemplateInvocationSpec> {

		static final MustacheFactory FACTORY = new DefaultMustacheFactory();
		private final String save, restore;

		TemplateInvocationSpec(BundleContext context, InvocationType type, CharSequence fieldName,
				TransformationTemplate template) {
			super(context, type, fieldName);
			this.save = template.save();
			this.restore = template.restore();
		}

		TemplateInvocationSpec(BundleContext context, InvocationType type, CharSequence fieldName,
				String save, String restore) {
			super(context, type, fieldName);
			this.save = save;
			this.restore = restore;
		}

		@Override
		protected String createStatement(CharSequence prepended, CharSequence appended) {
			final StringWriter writer = new StringWriter();
			final Scope scope = new Scope(context.sourceObjectName() + "." + fieldName.toString(),
					context.bundleObjectName());
			FACTORY.compile(new StringReader(type == InvocationType.SAVE ? save : restore), "")
					.execute(writer, scope);
			return writer.toString();
		}

		static class Scope {
			final String fieldName;
			final String bundle;

			private Scope(String fieldName, String bundle) {
				this.fieldName = fieldName;
				this.bundle = bundle;
			}
		}

		@Override
		public boolean invocationStatementMutable() {
			return false;
		}
	}

}
