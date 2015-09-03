package com.sora.util.akatsuki.compiler.models;

import com.sora.util.akatsuki.compiler.CodeGenerator;
import com.sora.util.akatsuki.compiler.ProcessorContext;

public abstract class GenerationTargetModel extends BaseModel implements CodeGenerator {

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
