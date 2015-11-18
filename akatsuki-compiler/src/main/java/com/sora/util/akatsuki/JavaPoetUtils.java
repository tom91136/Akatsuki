package com.sora.util.akatsuki;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

 class JavaPoetUtils {

	private static final String REPLACEMENT = Matcher.quoteReplacement("$$");
	private static final Pattern PATTERN = Pattern.compile("\\$");

	public static String escapeStatement(String statement) {
		return PATTERN.matcher(statement).replaceAll(REPLACEMENT);
	}

}
