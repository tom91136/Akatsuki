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

package com.sora.util.akatsuki.compiler;

import com.google.common.truth.Truth;
import com.google.testing.compile.CompileTester;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.FieldSpec.Builder;
import com.squareup.javapoet.TypeName;

import java.lang.annotation.Annotation;
import java.util.Collections;

import javax.annotation.processing.Processor;
import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;

import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;

/**
 * Project: Akatsuki Created by Tom on 7/26/2015.
 */
public class TestBase {

	public static final String TEST_PACKAGE = "test";
	public static final String TEST_CLASS = "TestClass";
	public static TypeName STRING_TYPE = ClassName.get(String.class);

	// creates a field spec
	public static FieldSpec field(TypeName typeName, String name,
			Class<? extends Annotation> annotation, Modifier... modifiers) {
		return field(typeName, name, annotation, null, modifiers);

	}

	// allows custom annotation spec
	public static FieldSpec field(TypeName typeName, String name, AnnotationSpec spec,
			Modifier... modifiers) {
		return FieldSpec.builder(typeName, name, modifiers).addAnnotation(spec).build();
	}

	// allows custom initializer
	public static FieldSpec field(TypeName typeName, String name,
			Class<? extends Annotation> annotation, String initializer, Modifier... modifiers) {
		final Builder builder = FieldSpec.builder(typeName, name, modifiers)
				.addAnnotation(annotation);
		if (initializer != null)
			builder.initializer(initializer);
		return builder.build();

	}

	public Iterable<Processor> processors() {
		return Collections.singletonList(new AkatsukiProcessor());
	}

	public CompileTester assertTestClass(JavaFileObject testClass) {
		return Truth.ASSERT.about(javaSource()).that(testClass).processedWith(processors());
	}

}
