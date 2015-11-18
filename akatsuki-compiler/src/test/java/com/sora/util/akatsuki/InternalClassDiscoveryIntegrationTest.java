package com.sora.util.akatsuki;

import static org.mockito.Mockito.mock;

import javax.lang.model.element.Modifier;

import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import android.app.Fragment;
import android.os.Bundle;

@RunWith(Theories.class)
public class InternalClassDiscoveryIntegrationTest extends IntegrationTestBase {

	// we just need something that @Arg supports natively
	public static class MockedFragment extends Fragment {

		public MockedFragment() {
			// super();
		}

	}

	private static class APITestBase extends BaseTestEnvironment {

		private Object instance;
		private Bundle mockedBundle;

		public APITestBase(IntegrationTestBase base, TestSource source, TestSource... required) {
			super(base, source, required);
			instantaiteInstance(null);
		}

		public APITestBase(IntegrationTestBase base, TestSource instanceSource, TestSource source,
				TestSource... required) {
			super(base, source, required);
			instantaiteInstance(instanceSource.fqcn());
		}

		private void instantaiteInstance(String instanceName) {
			String instanceFqcn = instanceName != null ? instanceName : sources.get(0).fqcn();
			try {
				instance = findClass(instanceFqcn).newInstance();
			} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
				throw new AssertionError("Unable to instantiate " + instanceFqcn, e);
			}
		}

		@Override
		protected void setupTestEnvironment() throws Exception {
			mockedBundle = mock(Bundle.class);
		}

		public Object instance() {
			return instance;
		}

		public Bundle mockedBundle() {
			return mockedBundle;
		}

	}

	interface APITestScenario {
		APITestBase create(IntegrationTestBase base);
	}

	@DataPoint
	public static APITestScenario createSimpleSource() {
		TestSource source = new TestSource("test", generateClassName(), Modifier.PUBLIC);
		source.appendFields(new RetainedTestField(String.class).createFieldSpec(),
				new ArgTestField(Integer.class).createFieldSpec())
				.appendTransformation((b, s) -> b.superclass(MockedFragment.class));

		return base -> new APITestBase(base, source);
	}

	@DataPoint
	public static APITestScenario createSourceWithInnerClass() {
		TestSource enclosingClass = new TestSource("test", generateClassName(), Modifier.PUBLIC);
		TestSource source = new TestSource(null, generateClassName(), Modifier.PUBLIC);
		source.appendFields(new RetainedTestField(String.class).createFieldSpec(),
				new ArgTestField(Integer.class).createFieldSpec())
				.appendTransformation((b, s) -> b.superclass(MockedFragment.class));
		enclosingClass.innerClasses(true, source);
		return base -> new APITestBase(base, source, enclosingClass, new TestSource[] {});
	}

//	@DataPoint
//	public static APITestScenario createSourceWithMultipleInnerClass() {
//		throw new RuntimeException();
//	}
//
//	@DataPoint
//	public static APITestScenario createSourceWithMultipleSameNameInnerClass() {
//		throw new RuntimeException();
//	}
//
//	@DataPoint
//	public static APITestScenario createSourceWithInnerClassAndInheritance() {
//		throw new RuntimeException();
//	}

	@Theory
	public void testSaveWithInstanceState(APITestScenario scenario)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		APITestBase base = scenario.create(this);
		ClassLoader loader = base.classLoader();
		Akatsuki.save(loader, base.instance(), base.mockedBundle());
		BundleRetainer<Object> retainer = Akatsuki.findRetainerInstance(loader, base.instance(),
				Retained.class);
		retainer.restore(base.instance(), base.mockedBundle());
	}

	@Theory
	public void testSaveWithArg(APITestScenario scenario)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		APITestBase base = scenario.create(this);
		ClassLoader loader = base.classLoader();

		BundleRetainer<Object> retainer = Akatsuki.findRetainerInstance(loader, base.instance(),
				Arg.class);
		retainer.restore(base.instance(), base.mockedBundle());

	}

	@Theory
	public void testSaveWithInstanceStateAndArg(APITestScenario scenario)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		APITestBase base = scenario.create(this);

		ClassLoader loader = base.classLoader();
		Akatsuki.save(loader, base.instance(), base.mockedBundle());
		BundleRetainer<Object> argRetainer = Akatsuki.findRetainerInstance(loader, base.instance(),
				Arg.class);
		BundleRetainer<Object> bundleRetainer = Akatsuki.findRetainerInstance(loader,
				base.instance(), Retained.class);
		argRetainer.restore(base.instance(), base.mockedBundle());
		bundleRetainer.restore(base.instance(), base.mockedBundle());
	}
}
