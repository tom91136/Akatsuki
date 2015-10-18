package com.sora.util.akatsuki.analyzers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.lang.model.type.TypeMirror;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Iterables;
import com.sora.util.akatsuki.analyzers.CascadingTypeAnalyzer.CodeTransform;
import com.sora.util.akatsuki.models.FieldModel;
import com.sora.util.akatsuki.models.FieldModel.Flag;

public class Element<T extends TypeMirror> {

	private final FieldModel model;

	private final List<CodeTransform> keyNameTransformations;
	private final List<CodeTransform> fieldAccessorTransformations;
	private String accessorExpression;

	private TypeMirror fieldMirror;
	private T refinedMirror;

	@SuppressWarnings("unchecked")
	public Element(FieldModel model) {
		this.model = model;
		this.keyNameTransformations = new ArrayList<>();
		this.fieldAccessorTransformations = new ArrayList<>();
		this.accessorExpression = "";
		this.fieldMirror = model.type();
		this.refinedMirror = (T) model.type();
	}

	// copy constructor
	@SuppressWarnings("unchecked")
	private Element(Element<?> element) {
		this.model = element.model;
		this.keyNameTransformations = new ArrayList<>(element.keyNameTransformations);
		this.fieldAccessorTransformations = new ArrayList<>(element.fieldAccessorTransformations);
		this.accessorExpression = element.accessorExpression;
		this.fieldMirror = element.fieldMirror;
		this.refinedMirror = (T) element.refinedMirror;
	}

	public javax.lang.model.element.Element originatingElement() {
		return model.element;
	}

	public FieldModel model() {
		return model;
	}

	public String accessor(Function<String, String> fieldAccessFunction) {
		String fieldAccess = fieldAccessFunction.apply(model.name() + accessorExpression);
		for (Function<String, String> transformations : fieldAccessorTransformations) {
			fieldAccess = transformations.apply(fieldAccess);
		}
		return fieldAccess;
	}

	public String uniqueName() {
		if (model.flags().contains(Flag.HIDDEN)) {
			// fieldName_packageName
			return model.name() + "_" + model.enclosingElement().getQualifiedName();
		} else {
			// fieldName
			return model.name();
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

	public <NT extends TypeMirror> Element<NT> refine(NT newTypeMirror) {
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

		private Element<T> element;

		private Builder(Element<T> element) {
			this.element = new Element<>(element);
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

		public Builder<T> fieldNameTransforms(CodeTransform transform) {
			element.fieldAccessorTransformations.add(transform);
			return this;
		}

		public Builder<T> accessor(SetterMode mode, String accessor) {
			element.accessorExpression = mode.process(element.accessorExpression, accessor);
			return this;
		}

		public Element<T> build() {
			final Element<T> element = this.element;
			// forbid mutation through the builder once the object has been
			// build
			this.element = null;
			return element;
		}

	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("keyNameTransformations", Iterables.size(keyNameTransformations))
				.add("accessorExpression", accessorExpression).add("fieldMirror", fieldMirror)
				.add("refinedMirror", refinedMirror).toString();
	}
}
