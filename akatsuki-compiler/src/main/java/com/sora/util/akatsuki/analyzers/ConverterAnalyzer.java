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

package com.sora.util.akatsuki.analyzers;

import java.util.HashMap;
import java.util.Map;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.sora.util.akatsuki.Akatsuki;
import com.sora.util.akatsuki.TransformationContext;
import com.sora.util.akatsuki.analyzers.CascadingTypeAnalyzer.Analysis;

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
		final Map<String, Object> scope = new HashMap<>();
		scope.put("class", converterElement.getQualifiedName() + ".class");
		scope.put("akatsuki", Akatsuki.class.getName());

		RawStatement statement;
		if (context.type == InvocationType.SAVE) {
			statement = new InvocationStatement(
					"{{akatsuki}}.converter({{class}}).save({{bundle}}, {{fieldName}}, {{keyName}});\n");
		} else {
			statement = new InvocationAssignmentStatement("{{fieldName}}",
					"{{akatsuki}}.converter({{class}}).restore({{bundle}}, {{fieldName}}, {{keyName}});\n");
		}
		return DefaultAnalysis.of(this, statement, context, scope);
	}
}
