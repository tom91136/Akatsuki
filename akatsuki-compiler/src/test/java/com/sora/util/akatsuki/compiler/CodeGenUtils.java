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

import com.google.testing.compile.JavaFileObjects;
import com.google.testing.compile.JavaSourcesSubject.SingleSourceAdapter;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeSpec.Builder;

import org.truth0.Truth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;

import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;

/**
 * Project: Akatsuki Created by Tom on 7/26/2015.
 */
public class CodeGenUtils {

	public static String createTestClass(String className, Iterable<FieldSpec> fieldSpecs) {
		TypeSpec testType = TypeSpec.classBuilder(TestBase.TEST_CLASS)
				.addModifiers(Modifier.PUBLIC, Modifier.FINAL).addFields(fieldSpecs).build();
		JavaFile javaFile = JavaFile.builder(TestBase.TEST_PACKAGE, testType).build();
		// javaFile.toString() does the same thing as using a string writer
		// internally
		return javaFile.toString();
	}

	public static String createTestClass(String className, FieldSpec... specs) {
		return createTestClass(className, Arrays.asList(specs));
	}

	public static JavaFileObject createTestClass(Iterable<FieldSpec> fieldSpecs) {
		return JavaFileObjects.forSourceString(TestBase.TEST_PACKAGE + "." + TestBase.TEST_CLASS,
				createTestClass(TestBase.TEST_CLASS, fieldSpecs));
	}

	public static JavaFileObject createTestClass(FieldSpec... specs) {
		return createTestClass(Arrays.asList(specs));
	}

	static SingleSourceAdapter testField(List<FieldSpec> specs) throws IOException {
		final JavaFileObject testClass = CodeGenUtils.createTestClass(specs);
		return Truth.ASSERT.about(javaSource()).that(testClass);
	}

	public static class JavaSource {

		public final String packageName;
		public final String className;
		public final List<FieldSpec> specs = new ArrayList<>();
		public final List<JavaSource> innerClasses = new ArrayList<>();
		public final Modifier[] modifiers;
		private JavaSource superClass;

		private BiFunction<TypeSpec.Builder, JavaSource, TypeSpec.Builder> builderTransformer;

		/**
		 * Creates a top level class
		 */
		public JavaSource(String packageName, String className, Modifier... modifiers) {
			this.packageName = packageName;
			this.className = className;
			this.modifiers = modifiers;
		}

		/**
		 * Creates a package-less class to be used as an static inner class
		 */
		public JavaSource(String className, Modifier... modifiers) {
			this.packageName = null;
			this.className = className;
			this.modifiers = modifiers;
		}

		public JavaSource fields(FieldSpec... specs) {
			this.specs.addAll(Arrays.asList(specs));
			return this;
		}

		public JavaSource fields(Collection<FieldSpec> specs) {
			this.specs.addAll(specs);
			return this;
		}

		public JavaSource innerClasses(JavaSource... sources) {
			return this.innerClasses(Arrays.asList(sources));
		}

		public JavaSource innerClasses(Collection<JavaSource> sources) {
			for (JavaSource source : sources) {
				if (source.packageName != null)
					throw new IllegalArgumentException("inner class " + source
							+ " contains a package name, it should be a top level class");
			}
			this.innerClasses.addAll(sources);
			return this;
		}

		public JavaSource superClass(JavaSource source) {
			source.checkInner();
			this.superClass = source;
			return this;
		}

		private TypeSpec.Builder specBuilder() {
			Builder builder = TypeSpec.classBuilder(className).addModifiers(modifiers)
					.addFields(specs).addTypes(innerClasses.stream()
							.map(s -> s.specBuilder().build()).collect(Collectors.toList()));

			if (superClass != null)
				builder.superclass(ClassName.get(superClass.packageName, superClass.className));
			if (builderTransformer != null)
				builder = builderTransformer.apply(builder, this);
			return builder;
		}

		public JavaSource builderTransformer(
				BiFunction<TypeSpec.Builder, JavaSource, TypeSpec.Builder> function) {
			this.builderTransformer = function;
			return this;
		}

		public String generateSource() {
			checkInner();
			JavaFile javaFile = JavaFile.builder(packageName, specBuilder().build()).build();
			return javaFile.toString();
		}

		public JavaFileObject generateFileObject(
				BiFunction<String, String, JavaFileObject> supplier) {
			checkInner();
			return supplier.apply(fqcn(), generateSource());
		}

		public JavaFileObject generateFileObject() {
			checkInner();
			return generateFileObject(JavaFileObjects::forSourceString);
		}

		private void checkInner() {
			if (packageName == null)
				throw new IllegalArgumentException("cannot be called on an inner class");
		}

		public String fqcn() {
			checkInner();
			return packageName + "." + className;
		}

	}

}
