package com.sora.util.akatsuki.compiler;

public abstract class GenerationTargetModel {

	protected final ProcessorContext context;

	protected GenerationTargetModel(ProcessorContext context) {
		this.context = context;
	}
}
