package com.sora.util.akatsuki.compiler;

import java.io.IOException;

import javax.annotation.processing.Filer;

public interface CodeGenerator {

	void writeSourceToFile(Filer filer) throws IOException;
}
