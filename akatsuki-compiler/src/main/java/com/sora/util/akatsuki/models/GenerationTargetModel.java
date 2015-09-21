package com.sora.util.akatsuki.models;

import com.sora.util.akatsuki.CodeGenerator;
import com.sora.util.akatsuki.ProcessorContext;

public abstract class GenerationTargetModel<M> extends BaseModel implements CodeGenerator<M> {

	private final SourceClassModel classModel;
	private final SourceTreeModel treeModel;

	protected GenerationTargetModel(ProcessorContext context, SourceClassModel classModel,
			SourceTreeModel treeModel) {
		super(context);
		this.classModel = classModel;
		this.treeModel = treeModel;
	}

	public SourceClassModel classModel() {
		return classModel;
	}

	public SourceTreeModel treeModel() {
		return treeModel;
	}

}
