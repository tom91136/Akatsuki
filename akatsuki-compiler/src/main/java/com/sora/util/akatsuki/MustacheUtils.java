package com.sora.util.akatsuki;


import com.samskivert.mustache.Mustache;

public class MustacheUtils {


	public static final Mustache.Compiler COMPILER = Mustache.compiler().escapeHTML(false);


	public static String render(Object scope, String template) {
		return COMPILER.compile(template).execute(scope);
	}

//	public static String render(Object scope, String template) {
//		final StringWriter writer = new StringWriter();
//		FACTORY.compile(new StringReader(template), "").execute(writer, scope);
//		return writer.toString();
//	}

}
