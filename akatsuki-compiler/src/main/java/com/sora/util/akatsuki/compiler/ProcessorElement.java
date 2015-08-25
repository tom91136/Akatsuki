package com.sora.util.akatsuki.compiler;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Iterables;
import com.sora.util.akatsuki.Retained;
import com.sora.util.akatsuki.compiler.transformations.CascadingTypeAnalyzer.CodeTransform;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

public class ProcessorElement<T extends TypeMirror> {

	private final Retained retained;

	private final Element originatingElement;
	private final TypeElement enclosingElement;

	private final List<CodeTransform> keyNameTransformations;
	private final List<CodeTransform> fieldAccessorTransformations;
	private String fieldName;
	private String accessorExpression;

	private TypeMirror fieldMirror;
	private T refinedMirror;

	private DeclaredType typeConverter;
	private boolean hidden = false;

	@SuppressWarnings("unchecked")
	public ProcessorElement(Retained retained, VariableElement element,
			DeclaredType typeConverter) {
		this.retained = retained;
		this.originatingElement = element;
		this.enclosingElement = ((TypeElement) element.getEnclosingElement());

		this.keyNameTransformations = new ArrayList<>();
		this.fieldAccessorTransformations = new ArrayList<>();
		this.fieldName = element.getSimpleName().toString();
		this.accessorExpression = "";
		this.fieldMirror = element.asType();
		this.refinedMirror = (T) element.asType();
		this.typeConverter = typeConverter;
	}

	// copy constructor
	@SuppressWarnings("unchecked")
	private ProcessorElement(ProcessorElement<?> element) {
		this.retained = element.retained;
		this.originatingElement = element.originatingElement;
		this.enclosingElement = element.enclosingElement;

		this.keyNameTransformations = new ArrayList<>(element.keyNameTransformations);
		this.fieldAccessorTransformations = new ArrayList<>(element.fieldAccessorTransformations);
		this.fieldName = element.fieldName;
		this.accessorExpression = element.accessorExpression;
		this.fieldMirror = element.fieldMirror;
		this.refinedMirror = (T) element.refinedMirror;
		this.typeConverter = element.typeConverter;
	}

	public Retained retained(){
		return retained;
	}

	public Element originatingElement() {
		return originatingElement;
	}

	public DeclaredType typeConverter() {
		return typeConverter;
	}

	public String fieldName() {
		return fieldName;
	}

	public String accessor(Function<String, String> fieldAccessFunction) {
		String fieldAccess = fieldAccessFunction.apply(fieldName + accessorExpression);
		for (Function<String, String> transformations : fieldAccessorTransformations) {
			fieldAccess = transformations.apply(fieldAccess);
		}
		return fieldAccess;
	}

	public String accessorExpression() {
		return accessorExpression;
	}

	public String uniqueName() {
		if (hidden) {
			// fieldName_packageName
			return fieldName + "_" + enclosingElement.getQualifiedName();
		} else {
			// fieldName
			return fieldName;
		}
	}

	public String keyName() {
		String keyName = "\"" + uniqueName() + "\"";
		for (Function<String, String> transformations : keyNameTransformations) {
			keyName = transformations.apply(keyName);
		}
		return keyName;
	}

	public TypeMirror fieldMirror() {
		return fieldMirror;
	}

	public T refinedMirror() {
		return refinedMirror;
	}

	public Builder<T> toBuilder() {
		return new Builder<>(this);
	}

	public boolean hidden() {
		return hidden;
	}

	public boolean notHidden() {
		return !hidden;
	}

	public void hidden(boolean hidden) {
		this.hidden = hidden;
	}

	public <NT extends TypeMirror> ProcessorElement<NT> refine(NT newTypeMirror) {
		return toBuilder().refinedType(newTypeMirror).build();
	}

	public static class Builder<T extends TypeMirror> {

		public enum SetterMode {
			APPEND((o, i) -> o + i), PREPEND((o, i) -> i + o), REPLACE((o, i) -> i);

			private final BiFunction<String, String, String> function;

			SetterMode(BiFunction<String, String, String> function) {
				this.function = function;
			}

			public String process(String original, String input) {
				return function.apply(original, input);
			}
		}

		private ProcessorElement<T> element;

		private Builder(ProcessorElement<T> element) {
			this.element = new ProcessorElement<>(element);
		}

		public <NT extends TypeMirror> Builder<NT> type(NT type) {
			return fieldType(type).refinedType(type);
		}

		public Builder<T> fieldType(TypeMirror mirror) {
			element.fieldMirror = mirror;
			return this;
		}

		@SuppressWarnings("unchecked")
		public <NT extends TypeMirror> Builder<NT> refinedType(NT mirror) {
			element.refinedMirror = (T) mirror;
			return (Builder<NT>) this;
		}

		public Builder<T> keyName(SetterMode mode, String name) {
			element.keyNameTransformations.add((original) -> mode.process(original, name));
			return this;
		}

		public Builder<T> fieldName(SetterMode mode, String name) {
			element.fieldName = mode.process(element.fieldName, name);
			return this;
		}

		public Builder<T> fieldNameTransforms(CodeTransform transform) {
			element.fieldAccessorTransformations.add(transform);
			return this;
		}

		public Builder<T> accessor(SetterMode mode, String accessor) {
			element.accessorExpression = mode.process(element.accessorExpression, accessor);
			return this;
		}

		public ProcessorElement<T> build() {
			final ProcessorElement<T> element = this.element;
			// forbid mutation through the builder once the object has been
			// build
			this.element = null;
			return element;
		}

	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("originatingElement", originatingElement)
				.add("enclosingElement", enclosingElement)
				.add("keyNameTransformations", Iterables.size(keyNameTransformations))
				.add("fieldName", fieldName).add("accessorExpression", accessorExpression)
				.add("fieldMirror", fieldMirror).add("refinedMirror", refinedMirror)
				.add("typeConverter", typeConverter).add("hidden", hidden).toString();
	}
}
