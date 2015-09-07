package com.sora.util.akatsuki.compiler;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import com.google.common.collect.ImmutableSet;

public class ProcessorUtils {

	private final ImmutableSet<TypeMirror> boxedTypes;

	private final Types types;
	private final Elements elements;

	ProcessorUtils(Types types, Elements elements) {
		this.types = types;
		this.elements = elements;

		boxedTypes = ImmutableSet.of(of(Boolean.class), of(Byte.class), of(Character.class),
				of(Float.class), of(Integer.class), of(Long.class), of(Short.class),
				of(Double.class));
	}

	public boolean isPrimitive(TypeMirror mirror) {
		return isBoxedType(mirror) || mirror.getKind().isPrimitive();
	}

	public boolean isBoxedType(TypeMirror mirror) {
		return mirror instanceof DeclaredType
				&& boxedTypes.stream().anyMatch(t -> isSameType(t, mirror, true));
	}

	public boolean isArray(TypeMirror mirror) {
		return mirror instanceof ArrayType;
	}

	public boolean isObject(TypeMirror mirror) {
		return mirror instanceof DeclaredType;
	}

	public boolean isAssignable(TypeMirror lhs, TypeMirror rhs, boolean ignoreGenericTypes) {
		if (ignoreGenericTypes) {
			lhs = toUnboundDeclaredType(lhs);
			rhs = toUnboundDeclaredType(rhs);
		}
		return types.isAssignable(lhs, rhs);
	}

	public boolean isSameType(TypeMirror lhs, TypeMirror rhs, boolean ignoreGenericTypes) {
		if (ignoreGenericTypes) {
			lhs = toUnboundDeclaredType(lhs);
			rhs = toUnboundDeclaredType(rhs);
		}
		return types.isSameType(lhs, rhs);
	}

	public boolean isSameType(TypeMirror target, boolean ignoreGenericTypes,
			TypeMirror... mirrors) {
		return Arrays.stream(mirrors).anyMatch(m -> isSameType(target, m, ignoreGenericTypes));
	}

	public DeclaredType getDeclaredType(DeclaredType mirror, TypeMirror... typeArguments) {
		return types.getDeclaredType((TypeElement) mirror.asElement(), typeArguments);
	}

	private TypeMirror toUnboundDeclaredType(TypeMirror mirror) {
		if (mirror.getKind() != TypeKind.DECLARED)
			return mirror;
		final List<? extends TypeMirror> lhsTypes = getGenericTypes(mirror);
		TypeElement lhsElement = (TypeElement) ((DeclaredType) mirror).asElement();
		return types.getDeclaredType(lhsElement, synthesizeUnboundType(lhsTypes.size()));
	}

	private TypeMirror[] synthesizeUnboundType(int count) {
		final WildcardType unboundWildcardType = types.getWildcardType(null, null);
		TypeMirror[] mirrors = new TypeMirror[count];
		for (int i = 0; i < mirrors.length; i++) {
			mirrors[i] = unboundWildcardType;
		}
		return mirrors;
	}

	public List<? extends TypeMirror> getGenericTypes(final TypeMirror mirror) {
		if (mirror instanceof DeclaredType) {
			return ((DeclaredType) mirror).getTypeArguments();
		} else {
			return Collections.emptyList();
		}
	}

	public TypeMirror of(Class<?> clazz) {
		return of(clazz.getName());
	}

	public TypeMirror of(CharSequence qualifiedName) {
		return elements.getTypeElement(qualifiedName).asType();
	}

	public DeclaredType getClassFromAnnotationMethod(Supplier<Class<?>> supplier) {
		// JDK suggested way of getting type mirrors, do not waste time here,
		// just move on
		try {
			supplier.get();
		} catch (MirroredTypeException e) {
			// types WILL be declared
			return (DeclaredType) e.getTypeMirror();
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public List<DeclaredType> getClassArrayFromAnnotationMethod(Supplier<Class<?>[]> supplier) {
		// JDK suggested way of getting type mirrors, do not waste time here,
		// just move on
		try {
			supplier.get();
		} catch (MirroredTypesException e) {
			// types WILL be declared
			return (List<DeclaredType>) e.getTypeMirrors();
		}
		return Collections.emptyList();
	}

}
