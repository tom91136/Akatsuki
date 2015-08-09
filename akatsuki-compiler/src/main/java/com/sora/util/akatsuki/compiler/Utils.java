package com.sora.util.akatsuki.compiler;

public class Utils {

	private Utils() {
		// no
	}

	public static String toCapitalCase(String source) {
		source = source.toLowerCase();
		source = Character.toString(source.charAt(0)).toUpperCase() + source.substring(1);
		return source;
	}

}
