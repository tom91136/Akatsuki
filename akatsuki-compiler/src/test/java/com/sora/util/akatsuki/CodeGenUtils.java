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

package com.sora.util.akatsuki;

import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;

import org.truth0.Truth;

import com.google.testing.compile.JavaFileObjects;
import com.google.testing.compile.JavaSourcesSubject.SingleSourceAdapter;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;

public class CodeGenUtils {

	public static String createTestSource(String className, Iterable<FieldSpec> fieldSpecs) {
		TypeSpec testType = TypeSpec.classBuilder(className)
				.addModifiers(Modifier.PUBLIC, Modifier.FINAL).addFields(fieldSpecs).build();
		JavaFile javaFile = JavaFile.builder(TestBase.TEST_PACKAGE, testType).build();
		// javaFile.toString() does the same thing as using a string writer
		// internally
		return javaFile.toString();
	}

	public static JavaFileObject createTestClass(Iterable<FieldSpec> fieldSpecs) {
		return JavaFileObjects.forSourceString(TestBase.TEST_PACKAGE + "." + TestBase.TEST_CLASS,
				createTestSource(TestBase.TEST_CLASS, fieldSpecs));
	}

	public static JavaFileObject createTestClass(FieldSpec... specs) {
		return createTestClass(Arrays.asList(specs));
	}

	static SingleSourceAdapter testField(List<FieldSpec> specs) throws IOException {
		final JavaFileObject testClass = CodeGenUtils.createTestClass(specs);
		return Truth.ASSERT.about(javaSource()).that(testClass);
	}

}
