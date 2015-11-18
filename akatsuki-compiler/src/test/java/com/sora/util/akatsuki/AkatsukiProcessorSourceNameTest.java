package com.sora.util.akatsuki;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import com.google.testing.compile.CompileTester.SuccessfulCompilationClause;
import com.google.testing.compile.JavaFileObjects;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeSpec.Builder;

@RunWith(Theories.class)
public class AkatsukiProcessorSourceNameTest extends IntegrationTestBase {

	static class GeneratedClassWithName {
		final JavaFileObject object;
		final String[] names;

		public GeneratedClassWithName(JavaFileObject object, String... names) {
			this.object = object;
			this.names = names;
		}

	}

	private static GeneratedClassWithName staticInnerClass(String... classes) {
		// maybe (String first, String...more) ?
		if (classes.length == 0)
			throw new IllegalArgumentException("");
		String[] generatedClasses = new String[classes.length];
		TypeSpec.Builder lastBuilder = null;
		for (int i = classes.length - 1; i >= 0; i--) {
			String clazz = classes[i];
			final Builder currentBuilder = TypeSpec.classBuilder(clazz)
					.addModifiers((i == 0 ? Modifier.PUBLIC : Modifier.STATIC))
					.addField(field(STRING_TYPE, clazz.toLowerCase(), Retained.class));
			if (lastBuilder != null) {
				currentBuilder.addType(lastBuilder.build());
			}
			// we generate static inner class names eg A.B -> A$B
			generatedClasses[i] = IntStream.range(0, i + 1).boxed().map(n -> classes[n])
					.collect(Collectors.joining("$"));
			lastBuilder = currentBuilder;
		}
		final JavaFile file = JavaFile.builder(TEST_PACKAGE, lastBuilder.build()).build();
		final JavaFileObject object = JavaFileObjects
				.forSourceString(TEST_PACKAGE + "." + classes[0], file.toString());

		return new GeneratedClassWithName(object, generatedClasses);
	}

	@DataPoint
	public static GeneratedClassWithName simpleClass() {
		final JavaFileObject object = CodeGenUtils
				.createTestClass(field(STRING_TYPE, "foo", Retained.class));
		return new GeneratedClassWithName(object, TEST_CLASS);
	}

	@DataPoint public static final GeneratedClassWithName STATIC_INNER_SIMPLE = staticInnerClass(
			"A", "B");
	@DataPoint public static final GeneratedClassWithName STATIC_INNER_COMPLEX = staticInnerClass(
			"A", "B", "C", "D");

	@Theory
	public void testSourceGeneratedWithCorrectName(GeneratedClassWithName generated) {
		final SuccessfulCompilationClause clause = assertTestClass(generated.object)
				.compilesWithoutError();
		for (String name : generated.names) {
			clause.and().generatesFileNamed(StandardLocation.SOURCE_OUTPUT, TEST_PACKAGE,
					Internal.generateRetainerClassName(name) + ".java");
		}
	}

}
