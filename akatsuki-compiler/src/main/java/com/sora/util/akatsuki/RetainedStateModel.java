package com.sora.util.akatsuki;

import java.io.IOException;
import java.util.EnumSet;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.processing.Filer;

import com.sora.util.akatsuki.BundleRetainerClassBuilder.AnalysisTransformation;
import com.sora.util.akatsuki.BundleRetainerClassBuilder.Direction;
import com.sora.util.akatsuki.Retained.RestorePolicy;
import com.sora.util.akatsuki.analyzers.CascadingTypeAnalyzer.Analysis;
import com.sora.util.akatsuki.analyzers.Element;
import com.sora.util.akatsuki.models.ClassInfo;
import com.sora.util.akatsuki.models.FieldModel;
import com.sora.util.akatsuki.models.SourceClassModel;
import com.sora.util.akatsuki.models.SourceMappingModel;
import com.sora.util.akatsuki.models.SourceTreeModel;
import com.squareup.javapoet.JavaFile;

public class RetainedStateModel extends SourceMappingModel
		implements AnalysisTransformation, Predicate<FieldModel> {

	private static final Function<ClassInfo, ClassInfo> CLASS_INFO_FUNCTION = info -> info
			.withNameTransform(Internal::generateRetainerClassName);

	private final ClassInfo info;
	private final RetainConfig config;

	RetainedStateModel(ProcessorContext context, SourceClassModel classModel,
			SourceTreeModel treeModel) {
		super(context, classModel, treeModel);
		this.info = CLASS_INFO_FUNCTION.apply(classModel().asClassInfo());
		this.config = classModel().annotation(RetainConfig.class)
				.orElse(context.config().retainConfig());
	}

	@Override
	public void writeToFile(Filer filer) throws IOException {
		if (!config.enabled()) {
			Log.verbose(context,
					"@Retained disabled for class " + classModel().asClassInfo() + ", skipping...");
			return;
		}
		BundleRetainerClassBuilder builder = new BundleRetainerClassBuilder(context, classModel(),
				EnumSet.allOf(Direction.class), CLASS_INFO_FUNCTION, CLASS_INFO_FUNCTION);

		builder.withFieldPredicate(this);
		builder.withAnalysisTransformation(this);

		JavaFile javaFile = JavaFile
				.builder(info.fullyQualifiedPackageName, builder.build().build()).build();
		javaFile.writeTo(filer);
	}

	@Override
	public ClassInfo classInfo() {
		return info;
	}

	@Override
	public void transform(ProcessorContext context, Direction direction, Element<?> element,
			Analysis analysis) {
		Retained retained = element.model().annotation(Retained.class)
				.orElseThrow(AssertionError::new);
		RestorePolicy policy = retained.restorePolicy();
		if (policy == RestorePolicy.DEFAULT) {
			// we might have a default in @RetainedConfig
			if (config.restorePolicy() != policy) {
				policy = config.restorePolicy();
			}
		}

		// policy only works on objects as primitives have default
		// values which we can't really check for :(
		if (!context.utils().isPrimitive(element.fieldMirror())) {
			switch (policy) {
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
				.map((retained) -> !retained.skip() && context.config().fieldAllowed(fieldModel))
				.orElse(false);
	}
}
