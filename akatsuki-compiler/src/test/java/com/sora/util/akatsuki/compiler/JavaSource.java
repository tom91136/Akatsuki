package com.sora.util.akatsuki.compiler;

import com.google.testing.compile.JavaFileObjects;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeSpec.Builder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;

public final class JavaSource {

	public final String packageName;
	public final String className;
	public final List<FieldSpec> specs = new ArrayList<>();
	public final List<JavaSource> innerClasses = new ArrayList<>();
	public final Modifier[] modifiers;
	private JavaSource superClass;

	private BiFunction<Builder, JavaSource, Builder> builderTransformer;

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

	private Builder specBuilder() {
		Builder builder = TypeSpec.classBuilder(className).addModifiers(modifiers).addFields(specs)
				.addTypes(innerClasses.stream().map(s -> s.specBuilder().build())
						.collect(Collectors.toList()));

		if (superClass != null)
			builder.superclass(ClassName.get(superClass.packageName, superClass.className));
		if (builderTransformer != null)
			builder = builderTransformer.apply(builder, this);
		return builder;
	}

	public JavaSource builderTransformer(BiFunction<Builder, JavaSource, Builder> function) {
		this.builderTransformer = function;
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
		checkInner();
		return packageName + "." + className;
	}

}
