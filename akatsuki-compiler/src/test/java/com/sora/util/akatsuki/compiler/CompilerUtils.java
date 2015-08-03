package com.sora.util.akatsuki.compiler;

import com.google.common.collect.ImmutableSet;

import java.util.Arrays;
import java.util.Locale;

import javax.annotation.processing.Processor;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import static com.google.common.base.Charsets.UTF_8;

/**
 * Project: Akatsuki Created by Tom on 7/31/2015.
 */
public class CompilerUtils {

	static ClassLoader compile(ClassLoader loader, Iterable<Processor> processors,
			JavaFileObject... objects) {
		// we need all this because we got a annotation processor, the generated
		// class has to go into memory too
		final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<JavaFileObject>();
		InMemoryJavaFileManager fileManager = new InMemoryJavaFileManager(
				compiler.getStandardFileManager(diagnosticCollector, Locale.getDefault(), UTF_8),
				loader);
		CompilationTask task = compiler.getTask(null, fileManager, diagnosticCollector, null,
				ImmutableSet.<String> of(), Arrays.asList(objects));
		task.setProcessors(processors);
		if (!task.call()) {
			throw new RuntimeException(
					"compilation failed: " + diagnosticCollector.getDiagnostics());
		} else {
			// fileManager.getOutputFiles().forEach(System.out::println);
			// System.out.println(diagnosticCollector.getDiagnostics());
		}
		return fileManager.getClassLoader(StandardLocation.CLASS_OUTPUT);
	}

}
