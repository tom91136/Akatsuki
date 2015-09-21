package com.sora.util.akatsuki.models;

import com.sora.util.akatsuki.ProcessorContext;

public class BaseModel {

	protected final ProcessorContext context;

	public BaseModel(ProcessorContext context) {
		this.context = context;
	}

	// TODO use me
	public void initialize() throws Exception {
		// not everyone needs to initialization
	}
}
