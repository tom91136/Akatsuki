package android.app;

import android.os.Bundle;

/**
 * For testing use only
 */
@SuppressWarnings("all")
public class Fragment implements android.content.ComponentCallbacks2,
		android.view.View.OnCreateContextMenuListener {
	public static class SavedState implements android.os.Parcelable {
		SavedState() {
			throw new RuntimeException("Stub!");
		}

		public int describeContents() {
			throw new RuntimeException("Stub!");
		}

		public void writeToParcel(android.os.Parcel dest, int flags) {
			throw new RuntimeException("Stub!");
		}

		public static final android.os.Parcelable.ClassLoaderCreator<android.app.Fragment.SavedState> CREATOR;

		static {
			CREATOR = null;
		}
	}

	public static class InstantiationException extends android.util.AndroidRuntimeException {
		public InstantiationException(java.lang.String msg, java.lang.Exception cause) {
			throw new RuntimeException("Stub!");
		}
	}

	public Fragment() {

	}

	public static android.app.Fragment instantiate(android.content.Context context,
			java.lang.String fname) {
		throw new RuntimeException("Stub!");
	}

	public static android.app.Fragment instantiate(android.content.Context context,
			java.lang.String fname, android.os.Bundle args) {
		throw new RuntimeException("Stub!");
	}

	public final boolean equals(java.lang.Object o) {
		throw new RuntimeException("Stub!");
	}

	public final int hashCode() {
		throw new RuntimeException("Stub!");
	}

	public java.lang.String toString() {
		throw new RuntimeException("Stub!");
	}

	public final int getId() {
		throw new RuntimeException("Stub!");
	}

	public final java.lang.String getTag() {
		throw new RuntimeException("Stub!");
	}


	private Bundle args;

	public void setArguments(android.os.Bundle args) {
		this.args = args;
	}

	public final android.os.Bundle getArguments() {
		return args;
	}

	public void setInitialSavedState(android.app.Fragment.SavedState state) {
		throw new RuntimeException("Stub!");
	}

	public void setTargetFragment(android.app.Fragment fragment, int requestCode) {
		throw new RuntimeException("Stub!");
	}

	public final android.app.Fragment getTargetFragment() {
		throw new RuntimeException("Stub!");
	}

	public final int getTargetRequestCode() {
		throw new RuntimeException("Stub!");
	}

	public final android.app.Activity getActivity() {
		throw new RuntimeException("Stub!");
	}

	public final android.content.res.Resources getResources() {
		throw new RuntimeException("Stub!");
	}

	public final java.lang.CharSequence getText(int resId) {
		throw new RuntimeException("Stub!");
	}

	public final java.lang.String getString(int resId) {
		throw new RuntimeException("Stub!");
	}

	public final java.lang.String getString(int resId, java.lang.Object... formatArgs) {
		throw new RuntimeException("Stub!");
	}

	public final android.app.FragmentManager getFragmentManager() {
		throw new RuntimeException("Stub!");
	}

	public final boolean isAdded() {
		throw new RuntimeException("Stub!");
	}

	public final boolean isDetached() {
		throw new RuntimeException("Stub!");
	}

	public final boolean isRemoving() {
		throw new RuntimeException("Stub!");
	}

	public final boolean isInLayout() {
		throw new RuntimeException("Stub!");
	}

	public final boolean isResumed() {
		throw new RuntimeException("Stub!");
	}

	public final boolean isVisible() {
		throw new RuntimeException("Stub!");
	}

	public final boolean isHidden() {
		throw new RuntimeException("Stub!");
	}

	public void onHiddenChanged(boolean hidden) {
		throw new RuntimeException("Stub!");
	}

	public void setRetainInstance(boolean retain) {
		throw new RuntimeException("Stub!");
	}

	public final boolean getRetainInstance() {
		throw new RuntimeException("Stub!");
	}

	public void setHasOptionsMenu(boolean hasMenu) {
		throw new RuntimeException("Stub!");
	}

	public void setMenuVisibility(boolean menuVisible) {
		throw new RuntimeException("Stub!");
	}

	public void setUserVisibleHint(boolean isVisibleToUser) {
		throw new RuntimeException("Stub!");
	}

	public boolean getUserVisibleHint() {
		throw new RuntimeException("Stub!");
	}

	public android.app.LoaderManager getLoaderManager() {
		throw new RuntimeException("Stub!");
	}

	public void startActivity(android.content.Intent intent) {
		throw new RuntimeException("Stub!");
	}

	public void startActivity(android.content.Intent intent, android.os.Bundle options) {
		throw new RuntimeException("Stub!");
	}

	public void startActivityForResult(android.content.Intent intent, int requestCode) {
		throw new RuntimeException("Stub!");
	}

	public void startActivityForResult(android.content.Intent intent, int requestCode,
			android.os.Bundle options) {
		throw new RuntimeException("Stub!");
	}

	public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
		throw new RuntimeException("Stub!");
	}

	@java.lang.Deprecated()
	public void onInflate(android.util.AttributeSet attrs, android.os.Bundle savedInstanceState) {
		throw new RuntimeException("Stub!");
	}

	public void onInflate(android.app.Activity activity, android.util.AttributeSet attrs,
			android.os.Bundle savedInstanceState) {
		throw new RuntimeException("Stub!");
	}

	public void onAttach(android.app.Activity activity) {
		throw new RuntimeException("Stub!");
	}

	public android.animation.Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
		throw new RuntimeException("Stub!");
	}

	public void onCreate(android.os.Bundle savedInstanceState) {
		throw new RuntimeException("Stub!");
	}

	public void onViewCreated(android.view.View view, android.os.Bundle savedInstanceState) {
		throw new RuntimeException("Stub!");
	}

	public android.view.View onCreateView(android.view.LayoutInflater inflater,
			android.view.ViewGroup container, android.os.Bundle savedInstanceState) {
		throw new RuntimeException("Stub!");
	}

	public android.view.View getView() {
		throw new RuntimeException("Stub!");
	}

	public void onActivityCreated(android.os.Bundle savedInstanceState) {
		throw new RuntimeException("Stub!");
	}

	public void onStart() {
		throw new RuntimeException("Stub!");
	}

	public void onResume() {
		throw new RuntimeException("Stub!");
	}

	public void onSaveInstanceState(android.os.Bundle outState) {
		throw new RuntimeException("Stub!");
	}

	public void onConfigurationChanged(android.content.res.Configuration newConfig) {
		throw new RuntimeException("Stub!");
	}

	public void onPause() {
		throw new RuntimeException("Stub!");
	}

	public void onStop() {
		throw new RuntimeException("Stub!");
	}

	public void onLowMemory() {
		throw new RuntimeException("Stub!");
	}

	public void onTrimMemory(int level) {
		throw new RuntimeException("Stub!");
	}

	public void onDestroyView() {
		throw new RuntimeException("Stub!");
	}

	public void onDestroy() {
		throw new RuntimeException("Stub!");
	}

	public void onDetach() {
		throw new RuntimeException("Stub!");
	}

	public void onCreateOptionsMenu(android.view.Menu menu, android.view.MenuInflater inflater) {
		throw new RuntimeException("Stub!");
	}

	public void onPrepareOptionsMenu(android.view.Menu menu) {
		throw new RuntimeException("Stub!");
	}

	public void onDestroyOptionsMenu() {
		throw new RuntimeException("Stub!");
	}

	public boolean onOptionsItemSelected(android.view.MenuItem item) {
		throw new RuntimeException("Stub!");
	}

	public void onOptionsMenuClosed(android.view.Menu menu) {
		throw new RuntimeException("Stub!");
	}

	public void onCreateContextMenu(android.view.ContextMenu menu, android.view.View v,
			android.view.ContextMenu.ContextMenuInfo menuInfo) {
		throw new RuntimeException("Stub!");
	}

	public void registerForContextMenu(android.view.View view) {
		throw new RuntimeException("Stub!");
	}

	public void unregisterForContextMenu(android.view.View view) {
		throw new RuntimeException("Stub!");
	}

	public boolean onContextItemSelected(android.view.MenuItem item) {
		throw new RuntimeException("Stub!");
	}

	public void dump(java.lang.String prefix, java.io.FileDescriptor fd, java.io.PrintWriter writer,
			java.lang.String[] args) {
		throw new RuntimeException("Stub!");
	}
}
