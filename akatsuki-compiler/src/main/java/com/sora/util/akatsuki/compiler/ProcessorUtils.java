package com.sora.util.akatsuki.compiler;

import com.google.common.collect.ImmutableSet;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
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

	public boolean elementContainsAnyAnnotation(Element element,
			Class<? extends Annotation> annotation) {
		return element.getEnclosedElements().stream()
				.anyMatch(e -> e.getAnnotation(annotation) != null);
	}

	public <T> Optional<T> traverseClassHierarchy(Element startingElement,
			BiPredicate<TypeMirror, TypeElement> stopPredicate,
			BiFunction<TypeMirror, TypeElement, T> transform) {
		if (!(startingElement instanceof TypeElement)
				|| startingElement.getKind() != ElementKind.CLASS)
			return Optional.empty();
		final TypeMirror superClassMirror = ((TypeElement) startingElement).getSuperclass();
		final Element superClassElement = types.asElement(superClassMirror);
		if (superClassElement != null && superClassElement.getKind() == ElementKind.CLASS) {
			if (!superClassMirror.equals(of(Object.class))
					&& stopPredicate.test(superClassMirror, (TypeElement) superClassElement)) {
				return Optional
						.of(transform.apply(superClassMirror, (TypeElement) superClassElement));
			} else {
				return traverseClassHierarchy(superClassElement, stopPredicate, transform);
			}
		} else {
			return Optional.empty();
		}
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

	public boolean isSimpleObject(TypeMirror mirror) {
		return isObject(mirror) && getGenericTypes(mirror).isEmpty();
	}

	public boolean isGenericObject(TypeMirror mirror) {
		return !getGenericTypes(mirror).isEmpty();
	}

	public boolean isAssignable(TypeMirror lhs, TypeMirror rhs, boolean ignoreGenericTypes) {
		if (ignoreGenericTypes) {
			lhs = toUnboundDeclaredType(lhs);
			rhs = toUnboundDeclaredType(rhs);
		}
		return types.isAssignable(lhs, rhs);
	}

	public boolean isAssignable(TypeMirror target, boolean ignoreGenericTypes,
			TypeMirror... mirrors) {
		return Arrays.stream(mirrors).anyMatch(m -> isAssignable(target, m, ignoreGenericTypes));
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
		final TypeElement typeElement = elements.getTypeElement(qualifiedName);
		if (typeElement == null)
			throw new NullPointerException("TypeElement of " + qualifiedName + " cannot be found!");
		return typeElement.asType();
	}

	public TypeMirror[] of(Class<?>... classes) {
		return Arrays.stream(classes).map(this::of).toArray(TypeMirror[]::new);
	}

	public TypeMirror[] of(CharSequence... qualifiedNames) {
		return Arrays.stream(qualifiedNames).map(this::of).toArray(TypeMirror[]::new);
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
