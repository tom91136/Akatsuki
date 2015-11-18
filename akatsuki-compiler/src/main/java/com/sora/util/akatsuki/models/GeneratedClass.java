package com.sora.util.akatsuki.models;

import java.io.IOException;

import javax.annotation.processing.Filer;

import com.sora.util.akatsuki.ProcessorContext;

public abstract class GeneratedClass extends BaseModel {

	protected GeneratedClass(ProcessorContext context) {
		super(context);
	}

	public abstract ClassInfo classInfo();

	public abstract void writeToFile(Filer filer) throws IOException;

}
