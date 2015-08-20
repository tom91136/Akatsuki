package com.sora.util.akatsuki.compiler;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class Utils {

	private Utils() {
		// no
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

}
