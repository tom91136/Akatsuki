package com.sora.util.akatsuki.compiler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Project: Akatsuki Created by Tom on 7/25/2015.
 */
public class JavaPoetUtils {

	private static final String REPLACEMENT = Matcher.quoteReplacement("$$");
	private static final Pattern PATTERN = Pattern.compile("\\$");

	public static String escapeStatement(String statement) {
		return PATTERN.matcher(statement).replaceAll(REPLACEMENT);
	}

}
