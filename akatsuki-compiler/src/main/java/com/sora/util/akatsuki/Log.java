package com.sora.util.akatsuki;

import javax.lang.model.element.Element;
import javax.tools.Diagnostic.Kind;

import com.sora.util.akatsuki.AkatsukiConfig.LoggingLevel;

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

	public static void warn(ProcessorContext context, String message) {
		print(context, message, null, LoggingLevel.WARN);
	}

	public static void warn(ProcessorContext context, String message, Element element) {
		print(context, message, element, LoggingLevel.WARN);
	}

	private static void print(ProcessorContext context, String message, Element element,
			LoggingLevel level) {
		// if we don't have config yet, print everything
		LoggingLevel loggingLevel = context.config() != null ? context.config().loggingLevel()
				: null;
		if (loggingLevel == null || level == loggingLevel) {
			String msg = level.name() + ":" + message;
			if (element == null) {
				context.messager().printMessage(Kind.OTHER, msg);
			} else {
				context.messager().printMessage(Kind.OTHER, msg, element);
			}
		}
	}

}
