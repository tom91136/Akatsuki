package com.sora.util.akatsuki.compiler;

import javax.lang.model.type.DeclaredType;

/**
 * Project: Akatsuki Created by tom91136 on 14/07/2015.
 */
public enum AndroidTypes {

	// @formatter:off
	Size("android.util.Size"),
	SizeF("android.util.SizeF"),
	String("java.lang.String"),
	CharSequence("java.lang.CharSequence"),
	IBinder("android.os.IBinder", "Binder"),
	Bundle("android.os.Bundle"),
	Parcelable("android.os.Parcelable"),
	Serializable("java.io.Serializable"),
	SparseArray("android.util.SparseArray");
	// @formatter:on

	public final CharSequence className;
	public final CharSequence typeAlias;

	AndroidTypes(CharSequence className) {
		this.className = className;
		this.typeAlias = null;
	}

	AndroidTypes(CharSequence className, CharSequence typeAlias) {
		this.className = className;
		this.typeAlias = typeAlias;
	}



	public DeclaredType asMirror(ProcessorContext context) {
		return (DeclaredType) context.utils().of(className);
	}

}
