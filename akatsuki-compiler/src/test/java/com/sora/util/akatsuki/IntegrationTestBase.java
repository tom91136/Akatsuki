package com.sora.util.akatsuki;

import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.processing.Processor;
import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;

import com.google.common.truth.Truth;
import com.google.testing.compile.CompileTester;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.FieldSpec.Builder;
import com.squareup.javapoet.TypeName;

// this class should not contain any static inner classes because @RunWith(Enclosed.class)
// with discover them and treat them as test classes
public abstract class IntegrationTestBase {

	public static final String TEST_PACKAGE = "test";
	public static final String TEST_CLASS = "TestClass";
	public static TypeName STRING_TYPE = ClassName.get(String.class);
	private static AtomicLong classIdentifier = new AtomicLong();

	// creates a field spec
	public static FieldSpec field(TypeName typeName, String name,
			Class<? extends Annotation> annotation, Modifier... modifiers) {
		return field(typeName, name, annotation, null, modifiers);

	}

	// allows custom annotation spec
	public static FieldSpec field(TypeName typeName, String name, AnnotationSpec spec,
			Modifier... modifiers) {
		final Builder builder = FieldSpec.builder(typeName, name, modifiers);
		if (spec != null)
			builder.addAnnotation(spec);
		return builder.build();
	}

	// allows custom initializer
	public static FieldSpec field(TypeName typeName, String name,
			Class<? extends Annotation> annotation, String initializer, Modifier... modifiers) {
		final Builder builder = FieldSpec.builder(typeName, name, modifiers);
		if (annotation != null)
			builder.addAnnotation(annotation);
		if (initializer != null)
			builder.initializer(initializer);
		return builder.build();
	}

	public static String generateClassName() {
		return TEST_CLASS + classIdentifier.incrementAndGet();
	}

	public Iterable<Processor> processors() {
		return Collections.singletonList(new AkatsukiProcessor());
	}

	public CompileTester assertTestClass(JavaFileObject testClass) {
		return Truth.ASSERT.about(javaSource()).that(testClass).processedWith(processors());
	}

}
