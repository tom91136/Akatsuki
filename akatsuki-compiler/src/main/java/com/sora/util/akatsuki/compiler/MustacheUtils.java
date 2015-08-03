package com.sora.util.akatsuki.compiler;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.MustacheFactory;

import java.io.StringReader;
import java.io.StringWriter;

/**
 * Project: Akatsuki Created by Tom on 7/23/2015.
 */
public class MustacheUtils {

	static final MustacheFactory FACTORY = new DefaultMustacheFactory();

	public static String render(Object scope, String template) {
		final StringWriter writer = new StringWriter();
		FACTORY.compile(new StringReader(template), "").execute(writer, scope);
		return writer.toString();
	}

//	public static String render(Object scope, String template) {
//		final StringWriter writer = new StringWriter();
//		FACTORY.compile(new StringReader(template), "").execute(writer, scope);
//		return writer.toString();
//	}

}
