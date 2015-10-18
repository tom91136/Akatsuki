package com.sora.util.akatsuki.models;

import java.util.function.Function;

public class ClassInfo {

	public final String fullyQualifiedPackageName;
	public final String className;

	public ClassInfo(String fullyQualifiedPackageName, String className) {
		this.fullyQualifiedPackageName = fullyQualifiedPackageName;
		this.className = className;
	}

	public String fullyQualifiedClassName() {
		return fullyQualifiedPackageName + "." + className;
	}

	public ClassInfo transform(Function<String, String> fqpnFunction,
			Function<String, String> classNameFunction) {
		return new ClassInfo(
				fqpnFunction == null ? fullyQualifiedPackageName
						: fqpnFunction.apply(fullyQualifiedPackageName),
				classNameFunction == null ? className : classNameFunction.apply(className));
	}

	@Override
	public String toString() {
		return fullyQualifiedClassName();
	}
}
