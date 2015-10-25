package com.sora.util.akatsuki;

import static org.mockito.Mockito.mock;

import javax.lang.model.element.Modifier;

import org.junit.Test;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.os.Parcelable;

public class PublicFacingApiIntegrationTest extends IntegrationTestBase {

	private static class APITestBase extends BaseTestEnvironment {

		public APITestBase(IntegrationTestBase base, TestSource source, TestSource... required) {
			super(base, source, required);
		}

		@Override
		protected void setupTestEnvironment() throws Exception {

		}

		@SuppressWarnings("unchecked")
		public <T> T createInstance()
				throws ClassNotFoundException, IllegalAccessException, InstantiationException {
			if (sources.size() != 1) {
				throw new AssertionError("incorrectly written test, more than one source used!");
			}
			return (T) findClass(sources.get(0).fqcn()).newInstance();
		}

	}

	// we just need something that @Arg supports natively
	public static class MockedFragment extends Fragment {

		public MockedFragment() {
			// super();
		}

	}

	private static TestSource createRetainedAndArgSource() {
		TestSource source = new TestSource("test", generateClassName(), Modifier.PUBLIC);
		source.appendFields(new RetainedTestField(String.class).createFieldSpec(),
				new ArgTestField(Integer.class).createFieldSpec())
				.appendTransformation((b, s) -> b.superclass(MockedFragment.class));
		return source;
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSaveObjectNullInstanceCheck() {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		Bundle bundle = mock(Bundle.class);
		Akatsuki.save(loader, (Object) null, bundle);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSaveViewNullInstanceCheck() {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		Bundle bundle = mock(Bundle.class);
		Akatsuki.save(loader, null, (Parcelable) bundle);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testRestoreNullInstanceCheck() {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		Bundle bundle = mock(Bundle.class);
		Akatsuki.restore(loader, null, bundle, bundle);
	}

	@Test
	public void testRestoreBundleNullSafe()
			throws IllegalAccessException, InstantiationException, ClassNotFoundException {
		Activity mock = mock(Activity.class);
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		Akatsuki.restore(loader, mock, null, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSaveBundleNullCheck()
			throws IllegalAccessException, InstantiationException, ClassNotFoundException {
		Activity mock = mock(Activity.class);
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		Akatsuki.save(loader, mock, null);
	}

	@Test
	public void testSaveWithInstanceState()
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		Bundle stateMock = mock(Bundle.class);
		TestSource source = createRetainedAndArgSource();
		APITestBase base = new APITestBase(this, source);
		MockedFragment instance = (MockedFragment) base.findClass(source.fqcn()).newInstance();
		ClassLoader loader = base.classLoader();
		Akatsuki.save(loader, instance, stateMock);
		BundleRetainer<MockedFragment> retainer = Akatsuki.findRetainerInstance(loader, instance,
				Retained.class);
		retainer.restore(instance, stateMock);
	}

	@Test
	public void testSaveWithArg()
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		Bundle argMock = mock(Bundle.class);
		TestSource source = createRetainedAndArgSource();
		APITestBase base = new APITestBase(this, source);
		MockedFragment instance = (MockedFragment) base.findClass(source.fqcn()).newInstance();
		ClassLoader loader = base.classLoader();
		BundleRetainer<MockedFragment> retainer = Akatsuki.findRetainerInstance(loader, instance,
				Arg.class);
		retainer.restore(instance, argMock);
	}

	@Test
	public void testSaveWithInstanceStateAndArg()
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		Bundle stateMock = mock(Bundle.class);
		Bundle argMock = mock(Bundle.class);
		TestSource source = createRetainedAndArgSource();
		APITestBase base = new APITestBase(this, source);
		MockedFragment instance = (MockedFragment) base.findClass(source.fqcn()).newInstance();
		ClassLoader loader = base.classLoader();
		Akatsuki.save(loader, instance, stateMock);
		BundleRetainer<MockedFragment> argRetainer = Akatsuki.findRetainerInstance(loader, instance,
				Arg.class);
		BundleRetainer<MockedFragment> bundleRetainer = Akatsuki.findRetainerInstance(loader,
				instance, Retained.class);
		argRetainer.restore(instance, stateMock);
		bundleRetainer.restore(instance, argMock);
	}
}
