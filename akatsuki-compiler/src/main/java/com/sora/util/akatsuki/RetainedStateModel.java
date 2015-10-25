package com.sora.util.akatsuki;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.processing.Filer;

import com.sora.util.akatsuki.BundleCodeGenerator.Action;
import com.sora.util.akatsuki.BundleCodeGenerator.FieldTransformation;
import com.sora.util.akatsuki.analyzers.CascadingTypeAnalyzer.Analysis;
import com.sora.util.akatsuki.analyzers.Element;
import com.sora.util.akatsuki.models.ClassInfo;
import com.sora.util.akatsuki.models.FieldModel;
import com.sora.util.akatsuki.models.GenerationTargetModel;
import com.sora.util.akatsuki.models.SourceClassModel;
import com.sora.util.akatsuki.models.SourceTreeModel;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;

public class RetainedStateModel extends GenerationTargetModel<TypeSpec>
		implements FieldTransformation, Predicate<FieldModel> {

	private BundleCodeGenerator generator;

	RetainedStateModel(ProcessorContext context, SourceClassModel classModel,
			SourceTreeModel treeModel, TypeAnalyzerResolver resolver) {
		super(context, classModel, treeModel);
		generator = new BundleCodeGenerator(context, classModel(), resolver, Optional.of(this),
				EnumSet.allOf(Action.class), Optional.of(this));
	}

	@Override
	public TypeSpec createModel() {
		return generator.createModel();
	}

	@Override
	public void writeSourceToFile(Filer filer) throws IOException {
		JavaFile javaFile = JavaFile
				.builder(generator.generatedClassInfo().fullyQualifiedPackageName, createModel())
				.build();
		javaFile.writeTo(filer);
	}

	public ClassInfo classInfo() {
		return generator.generatedClassInfo();
	}

	@Override
	public void transform(ProcessorContext context, Action action, Element<?> element,
			Analysis analysis) {
		Retained retained = element.model().annotation(Retained.class)
				.orElseThrow(AssertionError::new);
		// policy only works on objects as primitives have default
		// values which we can't really check for :(
		if (!context.utils().isPrimitive(element.fieldMirror())) {
			switch (retained.restorePolicy()) {
			case IF_NULL:
				analysis.wrap(s -> "if({{fieldName}} == null){\n" + s + "}\n");
				break;
			case IF_NOT_NULL:
				analysis.wrap(s -> "if({{fieldName}} != null){\n" + s + "}\n");
				break;
			default:
			case DEFAULT:
			case OVERWRITE:
				// do nothing
				break;
			}
		}
	}

	@Override
	public boolean test(FieldModel fieldModel) {
		return fieldModel.annotation(Retained.class)
				.map((retained) -> !retained.skip() && context.config().testField(fieldModel))
				.orElse(false);
	}
}
