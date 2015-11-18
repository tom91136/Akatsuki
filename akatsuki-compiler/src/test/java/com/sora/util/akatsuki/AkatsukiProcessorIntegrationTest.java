package com.sora.util.akatsuki;

public class AkatsukiProcessorIntegrationTest extends IntegrationTestBase {


// TODO not ready for this yet
//	// TODO might not be the best way to test this..
//	@Test(expected = AssertionError.class)
//	public void testSourceNotGeneratedWhenAllSkipped() throws IOException {
//		AnnotationSpec spec = AnnotationSpec.builder(Retained.class).addMember("skip", "$L", true)
//				.build();
//		final JavaFileObject testClass = CodeGenUtils
//				.createTestClass(field(STRING_TYPE, "foo", spec), field(STRING_TYPE, "bar", spec));
//
//		// the assertion error is expected because no file should be generated
//		assertTestClass(testClass).compilesWithoutError().and().generatesFileNamed(
//				StandardLocation.SOURCE_OUTPUT, TEST_PACKAGE,
//				Internal.generateRetainerClassName(TEST_CLASS) + ".java");
//	}

}