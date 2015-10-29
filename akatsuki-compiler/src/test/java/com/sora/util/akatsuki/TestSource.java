package com.sora.util.akatsuki;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;

import com.google.testing.compile.JavaFileObjects;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeSpec.Builder;

public final class TestSource {

	public final String packageName;
	public final String className;
	public final List<FieldSpec> specs = new ArrayList<>();
	public final List<TestSource> innerClasses = new ArrayList<>();
	public final Modifier[] modifiers;
	private TestSource superClass;
	private TestSource enclosingClass;

	private final List<BiFunction<Builder, TestSource, Builder>> builderTransformers = new ArrayList<>();

	/**
	 * Creates a top level class
	 */
	public TestSource(String packageName, String className, Modifier... modifiers) {
		this.packageName = packageName;
		this.className = className;
		this.modifiers = modifiers;
	}

	/**
	 * Creates a package-less class to be used as an static inner class
	 */
	public TestSource(String className, Modifier... modifiers) {
		this.packageName = null;
		this.className = className;
		this.modifiers = modifiers;
	}

	public TestSource appendFields(FieldSpec... specs) {
		this.specs.addAll(Arrays.asList(specs));
		return this;
	}

	public TestSource appendFields(Collection<FieldSpec> specs) {
		this.specs.addAll(specs);
		return this;
	}

	public TestSource appendTestFields(Collection<TestField> fields) {
		return appendFields(
				fields.stream().map(TestField::createFieldSpec).toArray(FieldSpec[]::new));
	}

	public TestSource appendTestFields(TestField... fields) {
		return appendFields(
				Arrays.stream(fields).map(TestField::createFieldSpec).toArray(FieldSpec[]::new));
	}

	public TestSource innerClasses(TestSource... sources) {
		return this.innerClasses(Arrays.asList(sources));
	}

	public TestSource innerClasses(Collection<TestSource> sources) {
		for (TestSource source : sources) {
			if (source.packageName != null)
				throw new IllegalArgumentException("inner class " + source
						+ " contains a package name, it should be a top level class");
			source.enclosingClass = this;
		}
		this.innerClasses.addAll(sources);
		return this;
	}

	public TestSource superClass(TestSource source) {
		source.checkInner();
		this.superClass = source;
		return this;
	}

	private Builder specBuilder() {
		Builder builder = TypeSpec.classBuilder(className).addModifiers(modifiers).addFields(specs)
				.addTypes(innerClasses.stream().map(s -> s.specBuilder().build())
						.collect(Collectors.toList()));

		if (superClass != null)
			builder.superclass(ClassName.get(superClass.packageName, superClass.className));

		for (BiFunction<Builder, TestSource, Builder> function : builderTransformers) {
			builder = function.apply(builder, this);
		}

		return builder;
	}

	public TestSource appendTransformation(BiFunction<Builder, TestSource, Builder> function) {
		this.builderTransformers.add(function);
		return this;
	}

	public String generateSource() {
		checkInner();
		JavaFile javaFile = JavaFile.builder(packageName, specBuilder().build()).build();
		return javaFile.toString();
	}

	public JavaFileObject generateFileObject(BiFunction<String, String, JavaFileObject> supplier) {
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
		return (enclosingClass != null) ? (enclosingClass.fqcn() + "$" + className)
				: (packageName + "" + "." + className);
	}

	@Override
	public String toString() {
		return "TestSource{" + "packageName='" + packageName + '\'' + ", className='" + className
				+ '\'' + ", specs=" + specs + ", innerClasses=" + innerClasses + ", modifiers="
				+ Arrays.toString(modifiers) + ", superClass=" + superClass + '}';
	}
}
