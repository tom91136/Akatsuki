package com.sora.util.akatsuki.compiler;

import javax.annotation.processing.Messager;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Project: Akatsuki Created by tom91136 on 15/07/2015.
 */
public interface ProcessorContext {

	Types types();

	Elements elements();

	ProcessorUtils utils();

	Messager messager();

}
