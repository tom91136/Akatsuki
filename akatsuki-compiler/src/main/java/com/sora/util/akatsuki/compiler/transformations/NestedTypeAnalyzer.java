package com.sora.util.akatsuki.compiler.transformations;

import com.sora.util.akatsuki.Akatsuki;
import com.sora.util.akatsuki.compiler.transformations.CascadingTypeAnalyzer.Analysis;
import com.sora.util.akatsuki.compiler.transformations.CascadingTypeAnalyzer.DefaultAnalysis.InvocationAssignmentExpression;
import com.sora.util.akatsuki.compiler.transformations.CascadingTypeAnalyzer.DefaultAnalysis.InvocationExpression;
import com.sora.util.akatsuki.compiler.transformations.CascadingTypeAnalyzer.DefaultAnalysis.RawExpression;

import java.util.Map;

import javax.lang.model.type.TypeMirror;

public class NestedTypeAnalyzer
		extends CascadingTypeAnalyzer<NestedTypeAnalyzer, TypeMirror, Analysis> {

	public NestedTypeAnalyzer(TransformationContext context) {
		super(context);
	}

	@Override
	protected NestedTypeAnalyzer createInstance(TransformationContext context) {
		return new NestedTypeAnalyzer(context);
	}

	@Override
	protected Analysis createAnalysis(InvocationContext<TypeMirror> context)
			throws UnknownTypeException {
		final Map<String, Object> scope = DefaultAnalysis.createScope(context.field,
				context.bundleContext);
		scope.put("akatsuki", Akatsuki.class.getName());

		RawExpression expression;
		if (context.type == InvocationType.SAVE) {
			expression = new InvocationExpression(
					"{{bundle}}.putBundle(\"{{keyName}}\", {{akatsuki}}.serialize({{fieldName}}))");
		} else {

			expression = new InvocationAssignmentExpression("{{fieldName}}",
					"{{akatsuki}}.deserialize({{fieldName}}, {{bundle}}.getBundle(\"{{keyName}}\"))");
		}
		return new DefaultAnalysis(scope, expression);
	}
}
