package com.sora.util.akatsuki;

public abstract class ArgConcludingBuilder<T> extends Internal.ArgBuilder<T> {

	public ArgConcludingBuilder() {

	}

	public static class VoidBuilder extends ArgConcludingBuilder<Void> {

		public VoidBuilder() {
			throw new RuntimeException(
					"This is only used in place for default annotation values, do not use!");
		}

		@Override
		protected Class<Void> targetClass() {
			return Void.class;
		}
	}

}
