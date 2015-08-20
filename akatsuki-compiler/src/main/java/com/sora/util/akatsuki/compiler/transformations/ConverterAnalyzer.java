/*
 * Copyright 2015 WEI CHEN LIN
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sora.util.akatsuki.compiler.transformations;

import com.sora.util.akatsuki.Akatsuki;
import com.sora.util.akatsuki.compiler.transformations.CascadingTypeAnalyzer.Analysis;
import com.sora.util.akatsuki.compiler.transformations.CascadingTypeAnalyzer.DefaultAnalysis.InvocationAssignmentExpression;
import com.sora.util.akatsuki.compiler.transformations.CascadingTypeAnalyzer.DefaultAnalysis.InvocationExpression;
import com.sora.util.akatsuki.compiler.transformations.CascadingTypeAnalyzer.DefaultAnalysis.RawExpression;

import java.util.Map;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

public class ConverterAnalyzer
		extends CascadingTypeAnalyzer<ConverterAnalyzer, TypeMirror, Analysis> {
	private final TypeElement converterElement;

	public ConverterAnalyzer(TransformationContext context, DeclaredType converterType) {
		super(context);
		this.converterElement = (TypeElement) converterType.asElement();
	}

	@Override
	protected ConverterAnalyzer createInstance(TransformationContext context) {
		return new ConverterAnalyzer(context, (DeclaredType) converterElement.asType());
	}

	@Override
	protected Analysis createAnalysis(InvocationContext<TypeMirror> context)
			throws UnknownTypeException {
		final Map<String, Object> scope = DefaultAnalysis.createScope(context.field,
				context.bundleContext);
		scope.put("class", converterElement.getQualifiedName() + ".class");
		scope.put("akatsuki", Akatsuki.class.getName());

		RawExpression expression;

		if (context.type == InvocationType.SAVE) {
			expression = new InvocationExpression(
					"{{akatsuki}}.converter({{class}}).save({{bundle}}, {{fieldName}}, {{keyName}});\n");
		} else {
			expression = new InvocationAssignmentExpression("{{fieldName}}", "{{akatsuki}}.converter({{class}}).restore({{bundle}}, {{fieldName}}, {{keyName}});\n");
			
		}
		return new DefaultAnalysis(scope, expression);
	}
}
