package com.sora.util.akatsuki.compiler.transformations;

import com.sora.util.akatsuki.compiler.BundleRetainerModel.Field;
import com.sora.util.akatsuki.compiler.ProcessorContext;

import javax.lang.model.type.TypeMirror;

public interface TransformationContext extends ProcessorContext {

	FieldTransformation<? extends TypeMirror> resolve(Field<?> field);

}
