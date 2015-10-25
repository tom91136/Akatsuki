package com.sora.util.akatsuki;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeVariableName;

public class SourceUtils {

	public static final TypeVariableName T = var("T");


	public static TypeVariableName var(String name){
		return TypeVariableName.get(name);
	}

	public static ParameterizedTypeName type(Class<?> clazz, TypeVariableName...variableNames){
		return ParameterizedTypeName.get(ClassName.get(clazz), variableNames);
	}

}
