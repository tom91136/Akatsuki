package com.sora.util.akatsuki.compiler.transformations;

import com.sora.util.akatsuki.Akatsuki;
import com.sora.util.akatsuki.compiler.InvocationSpec.InvocationType;
import com.sora.util.akatsuki.compiler.ProcessorContext;

import java.util.Map;

import javax.lang.model.type.TypeMirror;

public class NestedTransformation extends FieldTransformation<TypeMirror> {

	public NestedTransformation(ProcessorContext context) {
		super(context);
	}

	@Override
	protected Invocation createInvocation(InvocationContext<TypeMirror> context)
			throws UnknownTypeException {
		final Map<String, Object> scope = MustacheTemplateSupplier.createScope(context.field,
				context.bundleContext);
		scope.put("akatsuki", Akatsuki.class.getName());

		String template;
		if (context.type == InvocationType.SAVE) {
			template = "{{bundle}}.putBundle(\"{{keyName}}\", {{akatsuki}}.serialize({{fieldName}}))";
		} else {
			template = "{{fieldName}} = {{akatsuki}}.deserialize({{fieldName}}, {{bundle}}.getBundle(\"{{keyName}}\"))";
		}
		return new MustacheTemplateSupplier(scope, template);
	}
}
