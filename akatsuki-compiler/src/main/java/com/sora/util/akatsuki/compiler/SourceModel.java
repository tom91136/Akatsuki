package com.sora.util.akatsuki.compiler;

public abstract class SourceModel {

	protected final ProcessorContext context;

	protected SourceModel(ProcessorContext context) {
		this.context = context;
	}
}
