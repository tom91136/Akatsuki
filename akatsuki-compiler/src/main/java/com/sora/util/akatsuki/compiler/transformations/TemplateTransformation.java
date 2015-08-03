package com.sora.util.akatsuki.compiler.transformations;

import com.sora.util.akatsuki.TransformationTemplate;
import com.sora.util.akatsuki.compiler.InvocationSpec.InvocationType;
import com.sora.util.akatsuki.compiler.ProcessorContext;

import javax.lang.model.type.TypeMirror;

/**
 * Project: Akatsuki Created by Tom on 7/24/2015.
 */
public class TemplateTransformation extends FieldTransformation<TypeMirror> {

	public final TransformationTemplate template;

	public TemplateTransformation(ProcessorContext context, TransformationTemplate template) {
		super(context);
		this.template = template;
	}

	@Override
	protected Invocation createInvocation(InvocationContext<TypeMirror> context)
			throws UnknownTypeException {
		return new MustacheTemplateSupplier(
				MustacheTemplateSupplier.createScope(context.field, context.bundleContext),
				context.type == InvocationType.SAVE ? template.save() : template.restore());
	}
}
