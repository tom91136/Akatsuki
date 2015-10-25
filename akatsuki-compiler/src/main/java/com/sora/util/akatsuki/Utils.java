package com.sora.util.akatsuki;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

public class Utils {

	private Utils() {
		// no
	}

	public static String listSuperclass(TypeMirror mirror) {
		TypeMirror superclass = mirror;
		StringBuilder builder = new StringBuilder("Superclasses:[");
		while (superclass != null && superclass.getKind() == TypeKind.DECLARED) {
			builder.append(superclass.toString()).append(" <- ");
			superclass = ((TypeElement) ((DeclaredType) superclass).asElement()).getSuperclass();
		}
		builder.append("(42)]");
		return builder.toString();
	}

	public static String toCapitalCase(String source) {
		source = source.toLowerCase();
		source = Character.toString(source.charAt(0)).toUpperCase() + source.substring(1);
		return source;
	}

	@SuppressWarnings("unchecked")
	static class Defaults implements InvocationHandler {
		public static <A extends Annotation> A of(Class<A> annotation) {
			return (A) Proxy.newProxyInstance(annotation.getClassLoader(),
					new Class[] { annotation }, new Defaults());
		}

		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			return method.getDefaultValue();
		}
	}

	@SuppressWarnings("unchecked")
	static class Values<A extends Annotation> implements InvocationHandler {

		private final A defaults;
		private final Map<String, String> map;
		private final Function<String, String> nameTransformation;

		public Values(A defaults, Map<String, String> map,
				Function<String, String> nameTransformation) {
			this.defaults = defaults;
			this.map = Collections.unmodifiableMap(new HashMap<>(map));
			this.nameTransformation = nameTransformation;
		}

		public static <A extends Annotation> A of(Class<A> annotation, A defaults,
				Map<String, String> map, Function<String, String> nameTransformation) {
			return (A) Proxy.newProxyInstance(annotation.getClassLoader(),
					new Class[] { annotation }, new Values<>(defaults, map, nameTransformation));
		}

		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			String methodName = nameTransformation != null
					? nameTransformation.apply(method.getName()) : method.getName();
			if (!map.containsKey(methodName)) {
				if (defaults == null)
					throw new RuntimeException(
							"map does not contain method " + methodName + " map=" + map);
				return method.invoke(defaults);
			}
			String value = map.get(methodName);
			Class<?> returnType = method.getReturnType();
			if (returnType == String.class) {
				return value;
			} else if (returnType.isEnum()) {
				return Enum.valueOf((Class<Enum>) returnType, value);
			}else if(returnType.isArray() && returnType.getComponentType().isEnum()){
				// TODO test me
				return Arrays.stream(value.split(",")).map(s -> Enum.valueOf((Class<Enum>)returnType, s.trim())).toArray(Enum[]::new);
			} else {
				throw new UnsupportedOperationException(
						"return type of " + returnType + "is not implemented");
			}
		}
	}

}
