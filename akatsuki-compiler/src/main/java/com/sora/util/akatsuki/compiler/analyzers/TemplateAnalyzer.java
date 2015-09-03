package com.sora.util.akatsuki.compiler.analyzers;

import java.lang.annotation.Annotation;

import javax.lang.model.type.TypeMirror;

import com.google.common.base.Strings;
import com.sora.util.akatsuki.TransformationTemplate;
import com.sora.util.akatsuki.TransformationTemplate.StatementTemplate;
import com.sora.util.akatsuki.compiler.TransformationContext;
import com.sora.util.akatsuki.compiler.analyzers.CascadingTypeAnalyzer.Analysis;

public class TemplateAnalyzer
		extends CascadingTypeAnalyzer<TemplateAnalyzer, TypeMirror, Analysis> {

	public final TransformationTemplate template;

	public TemplateAnalyzer(TransformationContext context, TransformationTemplate template) {
		super(context);
		this.template = template;
	}

	@Override
	protected TemplateAnalyzer createInstance(TransformationContext context) {
		return new TemplateAnalyzer(context, template);
	}

	@Override
	protected Analysis createAnalysis(InvocationContext<TypeMirror> context)
			throws UnknownTypeException {
		RawStatement statement = toStatement(
				context.type == InvocationType.SAVE ? template.save() : template.restore());
		return DefaultAnalysis.of(this, statement, context, null);
	}

	private RawStatement toStatement(StatementTemplate template) {
		switch (template.type()) {
		case ASSIGNMENT:
			if (Strings.isNullOrEmpty(template.variable())) {
				throw new BadTemplateException(template,
						"Type.ASSIGNMENT cannot contain blank variable");
			}
			return new InvocationAssignmentStatement(template.variable(), template.value());
		default:
		case INVOCATION:
			if (!Strings.isNullOrEmpty(template.variable()))
				throw new BadTemplateException(template, "Type.INVOCATION cannot contain variable");
			return new InvocationStatement(template.value());
		}
	}

	private static class BadTemplateException extends RuntimeException {

		public BadTemplateException(Annotation annotation, String message) {
			super(message + "; Annotation:" + annotation);
		}
	}

}
