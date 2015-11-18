package com.sora.util.akatsuki;

import static org.mockito.Mockito.mock;

import org.junit.Test;

import android.app.Activity;
import android.os.Bundle;
import android.os.Parcelable;

public class PublicFacingApiIntegrationTest extends IntegrationTestBase {

	private static ClassLoader contextClassLoader() {
		return Thread.currentThread().getContextClassLoader();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSaveObjectNullInstanceCheck() {
		Bundle bundle = mock(Bundle.class);
		Akatsuki.save(contextClassLoader(), (Object) null, bundle);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSaveViewNullInstanceCheck() {
		Bundle bundle = mock(Bundle.class);
		Akatsuki.save(contextClassLoader(), null, (Parcelable) bundle);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testRestoreNullInstanceCheck() {
		Bundle bundle = mock(Bundle.class);
		Akatsuki.restore(contextClassLoader(), null, bundle, bundle);
	}

	@Test
	public void testRestoreBundleNullSafe()
			throws IllegalAccessException, InstantiationException, ClassNotFoundException {
		Activity mock = mock(Activity.class);
		Akatsuki.restore(contextClassLoader(), mock, null, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSaveBundleNullCheck()
			throws IllegalAccessException, InstantiationException, ClassNotFoundException {
		Activity mock = mock(Activity.class);
		ClassLoader loader = contextClassLoader();
		Akatsuki.save(loader, mock, null);
	}

}
