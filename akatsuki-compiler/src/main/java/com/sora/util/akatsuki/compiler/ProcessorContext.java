package com.sora.util.akatsuki.compiler;

import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Project: Akatsuki Created by tom91136 on 15/07/2015.
 */
public class ProcessorContext {

	private final ProcessingEnvironment environment;
	private final Types types;
	private final Elements elements;
	private final ProcessorUtils utils;

	public ProcessorContext(ProcessingEnvironment environment) {
		this.environment = environment;
		// this.types = environment.getTypeUtils();
		// this.elements = environment.getElementUtils();
		this.types = new SynchronizedTypes(environment.getTypeUtils());
		this.elements = new SynchronizedElements(environment.getElementUtils());
		this.utils = new ProcessorUtils(this.types, this.elements);

	}

	public ProcessorContext(ProcessorContext context) {
		this.environment = context.environment;
		this.types = context.types;
		this.elements = context.elements;
		this.utils = context.utils;
	}

	public Types types() {
		return types;
	}

	public Elements elements() {
		return elements;
	}

	public ProcessorUtils utils() {
		return utils;
	}

	public Messager messager() {
		return environment.getMessager();
	}

	class SynchronizedElements implements Elements {
		private final AtomicReference<Elements> elements;

		public SynchronizedElements(Elements elements) {
			this.elements = new AtomicReference<>(elements);
		}

		@Override
		public PackageElement getPackageElement(CharSequence name) {
			return elements.get().getPackageElement(name);
		}

		@Override
		public TypeElement getTypeElement(CharSequence name) {
			return elements.get().getTypeElement(name);
		}

		@Override
		public Map<? extends ExecutableElement, ? extends AnnotationValue> getElementValuesWithDefaults(
				AnnotationMirror a) {
			return elements.get().getElementValuesWithDefaults(a);
		}

		@Override
		public String getDocComment(Element e) {
			return elements.get().getDocComment(e);
		}

		@Override
		public boolean isDeprecated(Element e) {
			return elements.get().isDeprecated(e);
		}

		@Override
		public Name getBinaryName(TypeElement type) {
			return elements.get().getBinaryName(type);
		}

		@Override
		public PackageElement getPackageOf(Element type) {
			return elements.get().getPackageOf(type);
		}

		@Override
		public List<? extends Element> getAllMembers(TypeElement type) {
			return elements.get().getAllMembers(type);
		}

		@Override
		public List<? extends AnnotationMirror> getAllAnnotationMirrors(Element e) {
			return elements.get().getAllAnnotationMirrors(e);
		}

		@Override
		public boolean hides(Element hider, Element hidden) {
			return elements.get().hides(hider, hidden);
		}

		@Override
		public boolean overrides(ExecutableElement overrider, ExecutableElement overridden,
				TypeElement type) {
			return elements.get().overrides(overrider, overridden, type);
		}

		@Override
		public String getConstantExpression(Object value) {
			return elements.get().getConstantExpression(value);
		}

		@Override
		public void printElements(Writer w, Element... elements) {
			this.elements.get().printElements(w, elements);
		}

		@Override
		public Name getName(CharSequence cs) {
			return elements.get().getName(cs);
		}

		@Override
		public boolean isFunctionalInterface(TypeElement type) {
			return elements.get().isFunctionalInterface(type);
		}
	}

	class SynchronizedTypes implements Types {

		private final AtomicReference<Types> types;

		public SynchronizedTypes(Types types) {

			this.types = new AtomicReference<>(types);
		}

		@Override
		public Element asElement(TypeMirror t) {
			return types.get().asElement(t);
		}

		@Override
		public boolean isSameType(TypeMirror t1, TypeMirror t2) {
			return types.get().isSameType(t1, t2);
		}

		@Override
		public boolean isSubtype(TypeMirror t1, TypeMirror t2) {
			return types.get().isSubtype(t1, t2);
		}

		@Override
		public synchronized boolean isAssignable(TypeMirror t1, TypeMirror t2) {
			return types.get().isAssignable(t1, t2);
		}

		@Override
		public boolean contains(TypeMirror t1, TypeMirror t2) {
			return types.get().contains(t1, t2);
		}

		@Override
		public boolean isSubsignature(ExecutableType m1, ExecutableType m2) {
			return types.get().isSubsignature(m1, m2);
		}

		@Override
		public List<? extends TypeMirror> directSupertypes(TypeMirror t) {
			return types.get().directSupertypes(t);
		}

		@Override
		public TypeMirror erasure(TypeMirror t) {
			return types.get().erasure(t);
		}

		@Override
		public TypeElement boxedClass(PrimitiveType p) {
			return types.get().boxedClass(p);
		}

		@Override
		public PrimitiveType unboxedType(TypeMirror t) {
			return types.get().unboxedType(t);
		}

		@Override
		public TypeMirror capture(TypeMirror t) {
			return types.get().capture(t);
		}

		@Override
		public PrimitiveType getPrimitiveType(TypeKind kind) {
			return types.get().getPrimitiveType(kind);
		}

		@Override
		public NullType getNullType() {
			return types.get().getNullType();
		}

		@Override
		public NoType getNoType(TypeKind kind) {
			return types.get().getNoType(kind);
		}

		@Override
		public ArrayType getArrayType(TypeMirror componentType) {
			return types.get().getArrayType(componentType);
		}

		@Override
		public WildcardType getWildcardType(TypeMirror extendsBound, TypeMirror superBound) {
			return types.get().getWildcardType(extendsBound, superBound);
		}

		@Override
		public DeclaredType getDeclaredType(TypeElement typeElem, TypeMirror... typeArgs) {
			return types.get().getDeclaredType(typeElem, typeArgs);
		}

		@Override
		public DeclaredType getDeclaredType(DeclaredType containing, TypeElement typeElem,
				TypeMirror... typeArgs) {
			return types.get().getDeclaredType(containing, typeElem, typeArgs);
		}

		@Override
		public TypeMirror asMemberOf(DeclaredType containing, Element element) {
			return types.get().asMemberOf(containing, element);
		}

	}

}
