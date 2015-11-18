package com.sora.util.akatsuki.models;

import java.util.List;

import com.sora.util.akatsuki.ProcessorContext;

public abstract class SourceCollectingModel<T extends SourceMappingModel> extends GeneratedClass {

	private final List<T> mappingModels;

	protected SourceCollectingModel(ProcessorContext context, List<T> mappingModels) {
		super(context);
		this.mappingModels = mappingModels;
	}


	protected List<T> mappingModels() {
		return mappingModels;
	}
}
