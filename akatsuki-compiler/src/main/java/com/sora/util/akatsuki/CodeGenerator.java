package com.sora.util.akatsuki;

import java.io.IOException;

import javax.annotation.processing.Filer;

public interface CodeGenerator<M> {

	M createModel();

	void writeSourceToFile(Filer filer) throws IOException;
}
