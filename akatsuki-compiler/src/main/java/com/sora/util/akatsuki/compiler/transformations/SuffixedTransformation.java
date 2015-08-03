package com.sora.util.akatsuki.compiler.transformations;

import com.sora.util.akatsuki.compiler.ProcessorContext;

import javax.lang.model.type.TypeMirror;

/**
 * Project: Akatsuki Created by Tom on 7/24/2015.
 */
public abstract class SuffixedTransformation<T extends TypeMirror> extends FieldTransformation<T> {

	protected CharSequence suffix;
	protected TypeMirror methodMirror;
	protected boolean forceCast;

	public SuffixedTransformation(ProcessorContext context) {
		super(context);
	}

	public SuffixedTransformation<T> withSuffix(CharSequence suffix) {
		this.suffix = suffix;
		return this;
	}

	public SuffixedTransformation<T> withPossibleCast(TypeMirror methodMirror) {
		this.methodMirror = methodMirror;
		this.forceCast = false;
		return this;
	}

	public SuffixedTransformation<T> withForcedCast(TypeMirror methodMirror) {
		this.methodMirror = methodMirror;
		this.forceCast = true;
		return this;
	}
}
