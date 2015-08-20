package com.sora.util.akatsuki.compiler.transformations;

import com.sora.util.akatsuki.compiler.ProcessorContext;
import com.sora.util.akatsuki.compiler.ProcessorElement;
import com.sora.util.akatsuki.compiler.transformations.CascadingTypeAnalyzer.Analysis;

import javax.lang.model.type.TypeMirror;

public interface TransformationContext extends ProcessorContext {

	CascadingTypeAnalyzer<?, ? extends TypeMirror, ? extends Analysis> resolve(ProcessorElement<?> element);

}
