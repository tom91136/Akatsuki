package com.sora.util.akatsuki.compiler;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

import android.app.Fragment;
import android.os.Bundle;

import com.google.common.base.Strings;
import com.sora.util.akatsuki.Arg;
import com.sora.util.akatsuki.Internal;
import com.sora.util.akatsuki.Internal.ActivityConcludingBuilder;
import com.sora.util.akatsuki.Internal.FragmentConcludingBuilder;
import com.sora.util.akatsuki.Internal.ServiceConcludingBuilder;
import com.sora.util.akatsuki.compiler.BundleContext.SimpleBundleContext;
import com.sora.util.akatsuki.compiler.analyzers.CascadingTypeAnalyzer;
import com.sora.util.akatsuki.compiler.analyzers.CascadingTypeAnalyzer.Analysis;
import com.sora.util.akatsuki.compiler.analyzers.CascadingTypeAnalyzer.InvocationType;
import com.sora.util.akatsuki.compiler.analyzers.Element;
import com.sora.util.akatsuki.compiler.models.BaseModel;
import com.sora.util.akatsuki.compiler.models.FieldModel;
import com.sora.util.akatsuki.compiler.models.GenerationTargetModel;
import com.sora.util.akatsuki.compiler.models.SourceClassModel;
import com.sora.util.akatsuki.compiler.models.SourceTreeModel;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

// Spec: every package has it's own Builder...
public class ArgumentBuilderModel extends BaseModel implements CodeGenerator {

	private final SourceTreeModel treeModel;
	private final TypeAnalyzerResolver resolver;

	private final Map<TypeMirror, ClassName> supportedMap;

	public ArgumentBuilderModel(ProcessorContext context, SourceTreeModel treeModel,
			TypeAnalyzerResolver resolver) {
		super(context);
		this.treeModel = treeModel;
		this.resolver = resolver;

		Function<String, TypeMirror> mirror = name -> context.utils().of(name);

		supportedMap = new HashMap<>();
		supportedMap.put(mirror.apply("android.app.Fragment"),
				ClassName.get(FragmentConcludingBuilder.class));
		supportedMap.put(mirror.apply("android.support.v4.app.Fragment"),
				ClassName.get(FragmentConcludingBuilder.class));
		supportedMap.put(mirror.apply("android.app.Activity"),
				ClassName.get(ActivityConcludingBuilder.class));
		supportedMap.put(mirror.apply("android.app.Service"),
				ClassName.get(ServiceConcludingBuilder.class));

	}

	@Override
	public void writeSourceToFile(Filer filer) throws IOException {
		Map<String, List<SourceClassModel>> packageMap = treeModel.classModels().stream()
				.collect(Collectors.groupingBy(SourceClassModel::fullyQualifiedPackageName));

		String builderClassName = "Builders";

		for (Entry<String, List<SourceClassModel>> entry : packageMap.entrySet()) {
			TypeSpec.Builder buildersBuilder = TypeSpec.classBuilder(builderClassName)
					.addModifiers(Modifier.PUBLIC);
			for (SourceClassModel model : entry.getValue()) {
				if (!model.fields().stream().anyMatch(fm -> fm.hasAnnotation(Arg.class))) {
					continue;
				}
				TypeSpec builderSpec = createBuilderForModel(model).build();
				MethodSpec.Builder builderMethodBuilder = MethodSpec
						.methodBuilder(model.simpleName())
						.addCode("return new $L();", builderSpec.name)
						.addModifiers(Modifier.PUBLIC, Modifier.STATIC)
						.returns(ClassName.get(model.fullyQualifiedPackageName(), builderClassName,
								builderSpec.name));
				buildersBuilder.addMethod(builderMethodBuilder.build());

				buildersBuilder.addType(builderSpec);
			}
			JavaFile.builder(entry.getKey(), buildersBuilder.build()).build().writeTo(filer);
		}
	}

	private TypeSpec.Builder createBuilderForModel(SourceClassModel model) {

		// our target type's class
		ClassName targetTypeName = ClassName.get(model.originatingElement());

		String builderName = model.simpleName() + "Builder";
		final TypeSpec.Builder argBuilderTypeBuilder = TypeSpec.classBuilder(builderName)
				.addModifiers(Modifier.PUBLIC, Modifier.STATIC);

		SimpleBundleContext bundleContext = new SimpleBundleContext("", "bundle");

		ClassName concludingBuilderClass = supportedMap.keySet().stream()
				.filter(m -> context.utils().isAssignable(model.mirror(), m, true)).findFirst()
				.map(supportedMap::get).orElseThrow(RuntimeException::new);

		ParameterizedTypeName concludingBuilderTypeName = ParameterizedTypeName
				.get(concludingBuilderClass, targetTypeName);

		// last field returns a concluding builder
		TypeName previousName = concludingBuilderTypeName;

		List<FieldModel> fields = model.fields().stream().filter(fm -> fm.hasAnnotation(Arg.class))
				.collect(Collectors.toList());

		// last to first
		int last = fields.size() - 1;
		for (int i = last; i >= 0; i--) {
			FieldModel field = fields.get(i);
			Arg arg = field.annotation(Arg.class);

			Element<TypeMirror> element = new Element<>(field);
			String setterName = arg.value();
			if (Strings.isNullOrEmpty(setterName)) {
				setterName = field.name();
				// TODO allow and apply field name transform here
			}

			CascadingTypeAnalyzer<?, ? extends TypeMirror, ? extends Analysis> analyzer = resolver
					.resolve(element);
			Analysis analysis = analyzer.transform(bundleContext, element, InvocationType.SAVE);

			MethodSpec.Builder setterBuilder = MethodSpec.methodBuilder(setterName)
					.addModifiers(Modifier.PUBLIC)
					.addParameter(ClassName.get(field.type()), setterName).addCode(analysis.emit())
					.returns(previousName);

			if (i == 0) {
				// entry point is a method
				System.out.println("i->" + i + " last->" + last + " size->" + fields.size());
				if (i == last) {
					setterBuilder.addCode("return new $T($L, $L);", previousName, "bundle",
							"targetClass");
				} else {
					setterBuilder.addCode("return new $T();", previousName);

				}

				argBuilderTypeBuilder.addMethod(setterBuilder.build());

				// we're almost done
				// if the first field is optional, the whole builder can be
				// concluded without any setter, otherwise, implement out base
				// arg builder
				argBuilderTypeBuilder.superclass(arg.optional() ? concludingBuilderTypeName
						: ParameterizedTypeName.get(ClassName.get(Internal.ArgBuilder.class),
								targetTypeName));
				// and implement the constructor too
				argBuilderTypeBuilder.addMethod(MethodSpec.constructorBuilder()
						.addCode("super($L.class);", targetTypeName).build());
			} else {
				// start chaining
				TypeSpec.Builder builder = TypeSpec
						.classBuilder(Utils.toCapitalCase(setterName) + "Builder")
						.addModifiers(Modifier.PUBLIC);

				if (arg.optional())
					builder.superclass(previousName);
				if (i == last) {
					if (arg.optional()) {
						// last optional field builder needs to implement
						// concluding
						// builder
						builder.addMethod(MethodSpec.constructorBuilder()
								.addCode("super($1L.this" + ".$2L, " + "$1L.this.$3L);",
										builderName, "bundle", "targetClass")
								.build());
						setterBuilder.addCode("return this;");
					} else {
						// last non optioanl field builder needs to return a new
						// concluding builder
						setterBuilder.addCode("return new $T($L, $L);", previousName, "bundle",
								"targetClass");
					}

				} else {
					// everything in between returns the next builder
					setterBuilder.addCode("return new $T();", previousName);
				}

				builder.addMethod(setterBuilder.build());

				TypeSpec typeSpec = builder.build();
				argBuilderTypeBuilder.addType(typeSpec);

				previousName = ClassName.get(model.fullyQualifiedPackageName(), builderName,
						typeSpec.name);
			}
		}
		return argBuilderTypeBuilder;
	}

	private static class FragmentBuilderModel extends GenerationTargetModel {

		protected FragmentBuilderModel(ProcessorContext context, SourceClassModel classModel,
				SourceTreeModel treeModel) {
			super(context, classModel, treeModel);
		}

		@Override
		public void writeSourceToFile(Filer filer) throws IOException {

		}
	}

}
