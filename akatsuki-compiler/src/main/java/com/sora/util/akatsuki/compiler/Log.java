package com.sora.util.akatsuki.compiler;

import com.sora.util.akatsuki.Akatsuki.LoggingLevel;

import javax.lang.model.element.Element;
import javax.tools.Diagnostic.Kind;

public final class Log {

	public static void verbose(ProcessorContext context, String message) {
		print(context, message, null, LoggingLevel.VERBOSE);
	}

	public static void verbose(ProcessorContext context, String message, Element element) {
		print(context, message, element, LoggingLevel.VERBOSE);
	}

	public static void debug(ProcessorContext context, String message) {
		print(context, message, null, LoggingLevel.DEBUG);
	}

	public static void debug(ProcessorContext context, String message, Element element) {
		print(context, message, element, LoggingLevel.DEBUG);
	}

	private static void print(ProcessorContext context, String message, Element element,
			LoggingLevel level) {
		LoggingLevel loggingLevel = AkatsukiProcessor.retainConfig().loggingLevel();
		if (level == loggingLevel) {
			String msg = level.name() + ":" + message;
			if (element == null) {
				context.messager().printMessage(Kind.OTHER, msg);
			} else {
				context.messager().printMessage(Kind.OTHER, msg, element);
			}
		}
	}
}
