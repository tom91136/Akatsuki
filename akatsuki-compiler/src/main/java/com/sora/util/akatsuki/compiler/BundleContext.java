package com.sora.util.akatsuki.compiler;

/**
 * Project: Akatsuki Created by tom91136 on 15/07/2015.
 */
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
	}

}
