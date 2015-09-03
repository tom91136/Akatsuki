package com.sora.util.akatsuki.compiler.analyzers;

import java.util.HashMap;
import java.util.Map;

import javax.lang.model.type.TypeMirror;

import com.sora.util.akatsuki.Akatsuki;
import com.sora.util.akatsuki.compiler.TransformationContext;
import com.sora.util.akatsuki.compiler.analyzers.CascadingTypeAnalyzer.Analysis;

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
		final Map<String, Object> scope = new HashMap<>();
		scope.put("akatsuki", Akatsuki.class.getName());

		RawStatement statement;
		if (context.type == InvocationType.SAVE) {
			statement = new InvocationStatement(
					"{{bundle}}.putBundle({{keyName}}, {{akatsuki}}.serialize({{fieldName}}))");
		} else {

			statement = new InvocationAssignmentStatement("{{fieldName}}",
					"{{akatsuki}}.deserialize({{fieldName}}, {{bundle}}.getBundle({{keyName}}))");
		}
		return DefaultAnalysis.of(this, statement, context, scope);
	}
}
