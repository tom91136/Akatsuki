package com.sora.util.akatsuki.compiler.transformations;

import com.sora.util.akatsuki.TransformationTemplate;
import com.sora.util.akatsuki.compiler.transformations.CascadingTypeAnalyzer.Analysis;
import com.sora.util.akatsuki.compiler.transformations.CascadingTypeAnalyzer.DefaultAnalysis.InvocationExpression;

import javax.lang.model.type.TypeMirror;

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
		// TODO: 8/20/2015 this is broken, fix it
		return new DefaultAnalysis(
				DefaultAnalysis.createScope(context.field, context.bundleContext),
				new InvocationExpression((context.type == InvocationType.SAVE ? template.save()
						: template.restore())));
	}
}
