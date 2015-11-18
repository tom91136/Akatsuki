package com.sora.util.akatsuki;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeVariableName;

public class SourceUtils {

	public static final TypeVariableName T = var("T");


	public static TypeVariableName extend(String typeName, ClassName name) {
		return TypeVariableName.get(typeName, name);
	}

	public static TypeVariableName T_extends(ClassName name) {
		return extend("T", name);
	}


	public static TypeVariableName var(String name) {
		return TypeVariableName.get(name);
	}

	public static ParameterizedTypeName type(Class<?> clazz, TypeVariableName... variableNames) {
		return ParameterizedTypeName.get(ClassName.get(clazz), variableNames);
	}

	public static ParameterizedTypeName type(ClassName name, TypeVariableName... variableNames) {
		return ParameterizedTypeName.get(name, variableNames);
	}

}
