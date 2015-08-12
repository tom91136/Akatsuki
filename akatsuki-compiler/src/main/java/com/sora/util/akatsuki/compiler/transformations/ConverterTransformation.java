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
import com.sora.util.akatsuki.compiler.InvocationSpec.InvocationType;

import java.util.Map;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

public class ConverterTransformation extends FieldTransformation<TypeMirror> {
	private final TypeElement converterElement;

	public ConverterTransformation(TransformationContext context, DeclaredType converterType) {
		super(context);
		this.converterElement = (TypeElement) converterType.asElement();
	}

	@Override
	protected Invocation createInvocation(InvocationContext<TypeMirror> context)
			throws UnknownTypeException {
		final Map<String, Object> scope = MustacheTemplateSupplier.createScope(context.field,
				context.bundleContext);
		scope.put("class", converterElement.getQualifiedName() + ".class");
		scope.put("akatsuki", Akatsuki.class.getName());

		String template;
		if (context.type == InvocationType.SAVE) {
			template = "{{akatsuki}}.converter({{class}}).save({{bundle}}, {{fieldName}}, \"{{keyName}}\")";
		} else {
			template = "{{fieldName}} = {{akatsuki}}.converter({{class}}).restore({{bundle}}, {{fieldName}}, \"{{keyName}}\")";
		}
		return new MustacheTemplateSupplier(scope, template);
	}
}
