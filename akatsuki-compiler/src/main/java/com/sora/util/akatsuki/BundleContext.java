package com.sora.util.akatsuki;

import com.google.common.base.MoreObjects;


public interface BundleContext {

	String sourceObjectName();

	String bundleObjectName();

	class SimpleBundleContext implements BundleContext {

		private final String sourceObjectName;
		private final String bundleObjectName;

		SimpleBundleContext(String sourceObjectName, String bundleObjectName) {
			this.sourceObjectName = sourceObjectName;
			this.bundleObjectName = bundleObjectName;
		}

		@Override
		public String sourceObjectName() {
			return sourceObjectName;
		}

		@Override
		public String bundleObjectName() {
			return bundleObjectName;
		}

		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this).add("sourceObjectName", sourceObjectName)
					.add("bundleObjectName", bundleObjectName).toString();
		}
	}

}
