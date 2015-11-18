package com.sora.util.akatsuki.models;

import com.sora.util.akatsuki.ProcessorContext;

public abstract class SourceMappingModel extends GeneratedClass {

	private final SourceClassModel classModel;
	private final SourceTreeModel treeModel;

	protected SourceMappingModel(ProcessorContext context, SourceClassModel classModel,
			SourceTreeModel treeModel) {
		super(context);
		this.classModel = classModel;
		this.treeModel = treeModel;
	}

	public SourceClassModel classModel() {
		return classModel;
	}

	protected SourceTreeModel treeModel() {
		return treeModel;
	}

}
