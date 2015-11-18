package com.sora.util.akatsuki;

import static com.sora.util.akatsuki.SourceUtils.var;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;

import com.sora.util.akatsuki.models.ClassInfo;
import com.sora.util.akatsuki.models.SourceClassModel;
import com.sora.util.akatsuki.models.SourceCollectingModel;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;

class ArgumentBuildersModel extends SourceCollectingModel<ArgumentBuilderModel> {



	protected ArgumentBuildersModel(ProcessorContext context,
			List<ArgumentBuilderModel> mappingModels) {
		super(context, mappingModels);

	}

	public Map<String, TypeSpec> createModel() {

		Map<String, List<ArgumentBuilderModel>> groupedModel = mappingModels().stream()
				.collect(Collectors.groupingBy(m -> m.classInfo().fullyQualifiedPackageName));

		HashMap<String, TypeSpec> specHashMap = new HashMap<>();

		for (Entry<String, List<ArgumentBuilderModel>> entry : groupedModel.entrySet()) {

			TypeSpec.Builder buildersBuilder = TypeSpec.classBuilder(Internal.BUILDER_CLASS_NAME)
					.addModifiers(Modifier.PUBLIC);

			for (ArgumentBuilderModel model : entry.getValue()) {



				Log.verbose(context, "direct parent:" + model.classModel().directSuperModel());

				Optional<SourceClassModel> superModel = model.classModel()
						.directSuperModelWithAnnotation(Arg.class);

				if (!model.classModel().containsAnyAnnotation(Arg.class) && !superModel.isPresent()) {
					Log.verbose(context, "Class discarded: " + model.classInfo().className);
					//continue;
				}
				Log.verbose(context, "test write : " + model.classInfo().className);

				model.build().ifPresent(spec -> {
					buildersBuilder.addType(spec);
					Log.verbose(context, "write: " + model.classInfo().className);
					// abstract classes should not be built directly
					// TODO we need a test for this
					// if
					// (!model.classModel().containsModifier(Modifier.ABSTRACT))
					// {
					// TODO oh well, most of our argument test are based on
					// abstract classes, let's do it next time
					buildersBuilder.addMethod(createBuilderMethod(model, spec, true).build());
					buildersBuilder.addMethod(createBuilderMethod(model, spec, false).build());
					// }

				});
			}
			specHashMap.put(entry.getKey(), buildersBuilder.build());
		}
		return specHashMap;
	}

	private MethodSpec.Builder createBuilderMethod(ArgumentBuilderModel model, TypeSpec builderSpec,
			boolean withBundle) {

		MethodSpec.Builder builder = MethodSpec.methodBuilder(model.classModel().simpleName());
		if (withBundle) {
			builder.addCode("return new $L<>(bundle);", builderSpec.name)
					.addParameter(ClassName.get(AndroidTypes.Bundle.asMirror(context)), "bundle");
		} else {
			builder.addCode("return new $L<>(new Bundle());", builderSpec.name);
		}
		ClassName className = ClassName.get(model.classInfo().fullyQualifiedPackageName,
				Internal.BUILDER_CLASS_NAME, builderSpec.name);
		builder.addModifiers(Modifier.PUBLIC, Modifier.STATIC).returns(ParameterizedTypeName
				.get(className, ClassName.get(model.classModel().originatingElement()), var("?")));
		return builder;
	}

	@Override
	public ClassInfo classInfo() {
		return null;
	}

	@Override
	public void writeToFile(Filer filer) throws IOException {
		for (Entry<String, TypeSpec> entry : createModel().entrySet()) {
			JavaFile.builder(entry.getKey(), entry.getValue()).build().writeTo(filer);
		}
	}
}
