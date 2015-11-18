package com.sora.util.akatsuki.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import com.google.common.base.Joiner;
import com.squareup.javapoet.ClassName;

public class ClassInfo {

	public final String fullyQualifiedPackageName;
	public final String className;

	public final List<String> enclosingClasses;

	public ClassInfo(String fullyQualifiedPackageName, String className,
			List<String> enclosingClasses) {
		this.fullyQualifiedPackageName = fullyQualifiedPackageName;
		this.className = className;
		this.enclosingClasses = Collections.unmodifiableList(enclosingClasses);
	}

	public ClassInfo(String fullyQualifiedPackageName, String className,
			String... enclosingClasses) {
		this(fullyQualifiedPackageName, className, Arrays.asList(enclosingClasses));
	}

	public String fullyQualifiedClassName() {
		return fullyQualifiedPackageName + "."
				+ (enclosingClasses.size() == 0 ? "" : Joiner.on('$').join(enclosingClasses) + "$")
				+ className;
	}

	public ClassInfo withFqpnTransform(Function<String, String> function) {
		return new ClassInfo(function.apply(fullyQualifiedPackageName), className, enclosingClasses);
	}

	public ClassInfo withNameTransform(Function<String, String> function) {
		return new ClassInfo(fullyQualifiedPackageName, function.apply(className), enclosingClasses);
	}

	public ClassInfo withEnclosingClasses(String... classes) {

		ArrayList<String> c = new ArrayList<>(enclosingClasses);
		c.addAll(Arrays.asList(classes));

		return new ClassInfo(fullyQualifiedPackageName, className, c);
	}

	public ClassName toClassName() {
		if (enclosingClasses.isEmpty()) {
			return ClassName.get(fullyQualifiedPackageName, className);
		} else {
			// XXX this is just stupid, JavaPoet has a private constructor just
			// for this...
			ArrayList<String> names = new ArrayList<>(enclosingClasses);
			names.add(className);
			List<String> subList = names.subList(1, names.size());
			return ClassName.get(fullyQualifiedPackageName, names.get(0),
					subList.toArray(new String[subList.size()]));
		}
	}

	@Override
	public String toString() {
		return fullyQualifiedClassName();
	}
}
