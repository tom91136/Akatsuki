package com.sora.util.akatsuki;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import javax.tools.JavaFileObject;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.sora.util.akatsuki.CompilerUtils.Result;

public abstract class BaseTestEnvironment implements TestEnvironment {

	public static final Pattern NEW_LINE_PATTERN = Pattern.compile("\\r?\\n");

	protected final List<TestSource> sources;
	private final Result result;

	public BaseTestEnvironment(IntegrationTestBase base, List<TestSource> sources) {
		this.sources = Collections.unmodifiableList(sources);
		try {

			// find all top level classes
			JavaFileObject[] javaFileObjects = sources.stream().filter(TestSource::toplevel)
					.map(TestSource::generateFileObject).toArray(JavaFileObject[]::new);


			result = CompilerUtils.compile(Thread.currentThread().getContextClassLoader(),
					base.processors(), ImmutableList.of("-Aakatsuki.loggingLevel=VERBOSE"),
					javaFileObjects);
			if (result.compilationException != null)
				throw result.compilationException;
		} catch (Exception e) {
			throw new RuntimeException("Compilation was unsuccessful." + printReport(), e);
		}
		System.out.println(printReport());
		initialize();
	}

	public BaseTestEnvironment(IntegrationTestBase base, TestSource source,
			TestSource... required) {
		this(base, Lists.asList(source, required));
	}

	private void initialize() {
		try {
			setupTestEnvironment();
		} catch (Exception e) {
			throw new RuntimeException(
					"Compilation was successful but an error occurred while setting up the test "
							+ "environment." + printReport(),
					e);
		}
	}

	protected abstract void setupTestEnvironment() throws Exception;

	public Class<?> findClass(String className) throws ClassNotFoundException {
		return result.classLoader.loadClass(className);
	}

	public ClassLoader classLoader() {
		return result.classLoader;
	}

	public ImmutableList<JavaFileObject> generatedSources() {
		return result.sources;
	}

	public Class<?>[] sourceClasses() throws Exception {
		return sources.stream().map(s -> {
			try {
				return findClass(s.fqcn());
			} catch (ClassNotFoundException e) {
				// damn lambda
				throw new RuntimeException(e);
			}
		}).toArray(Class[]::new);
	}

	@Override
	public String printReport() {
		try {
			StringBuilder builder = new StringBuilder("\n=======Test status report=======");
			builder.append("\n\u2022Compiler Input:\n");
			for (TestSource source : sources) {
				builder.append("\n\tFully qualified name: ").append(source.fqcn()).append("\n");
				final String sourceCode = source.generateSource();
				final String[] lines = NEW_LINE_PATTERN.split(sourceCode);
				String format = String.format("%%0%dd", String.valueOf(lines.length).length());
				for (int i = 0; i < lines.length; i++) {
					builder.append("\t\t").append(String.format(format, i + 1)).append('.')
							.append(lines[i]);
					if (i != lines.length - 1)
						builder.append("\n");
				}
			}
			builder.append("\n\u2022Annotation processor output:");
			if (result == null || result.sources.isEmpty()) {
				builder.append("\n\tNo sources generated.");
			} else {
				builder.append(result.printGeneratedSources("\t"));
			}
			return builder.toString();
		} catch (Exception e) {
			return "[Unable to print report for sources:" + sources + " due to exception: "
					+ Throwables.getStackTraceAsString(e) + "]";
		}
	}

}