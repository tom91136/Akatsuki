package com.sora.util.akatsuki;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.google.common.base.Strings;
import com.sora.util.akatsuki.ArgConcludingBuilder.VoidBuilder;
import com.sora.util.akatsuki.ArgConfig.Order;
import com.sora.util.akatsuki.ArgConfig.Sort;
import com.sora.util.akatsuki.BundleContext.SimpleBundleContext;
import com.sora.util.akatsuki.BundleRetainerModel.Action;
import com.sora.util.akatsuki.Internal.ActivityConcludingBuilder;
import com.sora.util.akatsuki.Internal.ClassArgBuilder;
import com.sora.util.akatsuki.Internal.FragmentConcludingBuilder;
import com.sora.util.akatsuki.Internal.ServiceConcludingBuilder;
import com.sora.util.akatsuki.analyzers.CascadingTypeAnalyzer;
import com.sora.util.akatsuki.analyzers.CascadingTypeAnalyzer.Analysis;
import com.sora.util.akatsuki.analyzers.CascadingTypeAnalyzer.InvocationType;
import com.sora.util.akatsuki.analyzers.Element;
import com.sora.util.akatsuki.models.BaseModel;
import com.sora.util.akatsuki.models.FieldModel;
import com.sora.util.akatsuki.models.SourceClassModel;
import com.sora.util.akatsuki.models.SourceTreeModel;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeSpec.Builder;

// Spec: every package has it's own Builder...
public class ArgumentBuilderModel extends BaseModel
		implements CodeGenerator<Map<String, TypeSpec>> {

	private final SourceTreeModel treeModel;
	private final TypeAnalyzerResolver resolver;

	private final Map<TypeMirror, DeclaredType> supportedMap;

	public ArgumentBuilderModel(ProcessorContext context, SourceTreeModel treeModel,
			TypeAnalyzerResolver resolver) {
		super(context);
		this.treeModel = treeModel;
		this.resolver = resolver;

		supportedMap = new HashMap<>();
		supportedMap.put(mirror("android.app.Fragment"), mirror(FragmentConcludingBuilder.class));
		supportedMap.put(mirror("android.support.v4.app.Fragment"),
				mirror(FragmentConcludingBuilder.class));
		supportedMap.put(mirror("android.app.Activity"), mirror(ActivityConcludingBuilder.class));
		supportedMap.put(mirror("android.app.Service"), mirror(ServiceConcludingBuilder.class));
	}

	@SuppressWarnings("unchecked")
	private <T extends TypeMirror> T mirror(Class<?> clazz) {
		return (T) context.utils().of(clazz);
	}

	@SuppressWarnings("unchecked")
	private <T extends TypeMirror> T mirror(String className) {
		return (T) context.utils().of(className);
	}

	@Override
	public Map<String, TypeSpec> createModel() {
		Map<String, List<SourceClassModel>> packageMap = treeModel.classModels().stream()
				.collect(Collectors.groupingBy(SourceClassModel::fullyQualifiedPackageName));
		String builderClassName = "Builders";
		HashMap<String, TypeSpec> specHashMap = new HashMap<>();
		for (Entry<String, List<SourceClassModel>> entry : packageMap.entrySet()) {
			TypeSpec.Builder buildersBuilder = TypeSpec.classBuilder(builderClassName)
					.addModifiers(Modifier.PUBLIC);
			for (SourceClassModel model : entry.getValue()) {
				if (!model.fields().stream().anyMatch(fm -> fm.annotation(Arg.class).isPresent())) {
					continue;
				}
				ArgConfig config = model.originatingElement().getAnnotation(ArgConfig.class);
				if (config == null)
					config = Utils.Defaults.of(ArgConfig.class);

				TypeSpec builderSpec = createBuilderForModel(model, config).build();
				MethodSpec.Builder builderMethodBuilder = MethodSpec
						.methodBuilder(model.simpleName())
						.addCode("return new $L();", builderSpec.name)
						.addModifiers(Modifier.PUBLIC, Modifier.STATIC)
						.returns(ClassName.get(model.fullyQualifiedPackageName(), builderClassName,
								builderSpec.name));
				buildersBuilder.addMethod(builderMethodBuilder.build());
				buildersBuilder.addType(builderSpec);

			}
			specHashMap.put(entry.getKey(), buildersBuilder.build());
		}
		return specHashMap;
	}

	@Override
	public void writeSourceToFile(Filer filer) throws IOException {
		for (Entry<String, TypeSpec> entry : createModel().entrySet()) {
			JavaFile.builder(entry.getKey(), entry.getValue()).build().writeTo(filer);
		}
	}

	private TypeSpec.Builder createBuilderForModel(SourceClassModel model, ArgConfig config) {

		// our target type's class
		ClassName targetTypeName = ClassName.get(model.originatingElement());

		// generate builder's name
		String builderName = model.simpleName() + "Builder";

		final TypeSpec.Builder builderTypeBuilder = TypeSpec.classBuilder(builderName)
				.addModifiers(Modifier.STATIC);

		SimpleBundleContext bundleContext = new SimpleBundleContext("", "bundle()");

		// filter, sort, and order our fields
		Predicate<FieldModel> modelPredicate = fm -> fm.annotation(Arg.class).isPresent();
		Stream<FieldModel> fieldStream = model.fields().stream().filter(modelPredicate);
		final Sort sort = config.sort();
		final List<FieldModel> fields;
		switch (sort) {
		case CODE:
			// don't touch the list
			fields = fieldStream.collect(Collectors.toList());
			break;
		case RANDOM:
			// who uses this anyway?
			fields = fieldStream.collect(Collectors.toList());
			Collections.shuffle(fields, new Random());
			break;
		default:
			fields = fieldStream.sorted((lhs, rhs) -> {
				switch (sort) {
				case INDEX:
					return Integer.compare(lhs.annotation(Arg.class).get().index(),
							rhs.annotation(Arg.class).get().index());
				case LEXICOGRAPHICAL:
					return lhs.name().compareTo(rhs.name());
				default:
					throw new AssertionError("Unexpected sort type:" + sort);
				}
			}).collect(Collectors.toList());
			break;

		}

		builderTypeBuilder.addType(new BundleRetainerModel(context, model, treeModel, resolver,
				Optional.of(modelPredicate), EnumSet.of(Action.RESTORE), Optional.empty())
						.createModel().toBuilder().addModifiers(Modifier.STATIC).build());

		if (config.order() == Order.DSC)
			Collections.reverse(fields);

		final Optional<DeclaredType> possibleConcluderType = context.utils()
				.getClassFromAnnotationMethod(config::concludingBuilder, VoidBuilder.class);

		DeclaredType concluderType = possibleConcluderType.orElse(supportedMap.keySet().stream()
				.filter(m -> context.utils().isAssignable(model.mirror(), m, true)).findFirst()
				.map(supportedMap::get).orElseThrow(RuntimeException::new));

		final BuilderCodeGenerator generator;
		switch (config.type().returnType) {

		case CHAINED:
			// chained builder
			generator = new ChainedBuilderGenerator();
			break;
		case VOID:
			// pain old builder
			generator = new VoidBuilderGenerator();
			break;
		default:
		case SUBCLASSED:
			// advanced stuff
			generator = new SubclassBuilderGenerator();
			break;
		}

		generator.build(new PartialModel(model.fullyQualifiedPackageName(), builderName,
				targetTypeName, concluderType), builderTypeBuilder, bundleContext, fields);
		return builderTypeBuilder;
	}

	private static class PartialModel {
		final String builderFqpn;
		final String builderSimpleName;
		final ClassName targetClassName;
		final DeclaredType concludingBuilder;

		public PartialModel(String builderFqpn, String builderSimpleName, ClassName targetClassName,
				DeclaredType concludingBuilder) {
			this.builderFqpn = builderFqpn;
			this.builderSimpleName = builderSimpleName;
			this.targetClassName = targetClassName;
			this.concludingBuilder = concludingBuilder;
		}

		public ParameterizedTypeName concludingBuilderTypeName() {
			return ParameterizedTypeName.get(
					ClassName.get((TypeElement) concludingBuilder.asElement()), targetClassName);
		}

		public ClassName get(String name) {
			return ClassName.get(builderFqpn, builderSimpleName, name);
		}

		private void extendConcludingBuilder(ProcessorContext context, Builder builderTypeBuilder,
				DeclaredType concludingBuilder) {
			if (context.utils().isAssignable(concludingBuilder,
					context.utils().of(ClassArgBuilder.class), true)) {
				// implement the constructor with class parameters
				builderTypeBuilder.addMethod(MethodSpec.constructorBuilder()
						.addCode("super($L.class);" + "", targetClassName).build());
			} else if (context.utils().isAssignable(concludingBuilder,
					context.utils().of(ArgConcludingBuilder.class), true)) {
				// this is probably an user implemented one
				builderTypeBuilder.addMethod(MethodSpec.constructorBuilder()
						.addCode("this.bundle = new Bundle();").build());
			} else {
				throw new AssertionError(concludingBuilder
						+ " is not supported, @ArgConfig should not allow this in the first place...");
			}
		}

		private void extendConcludingBuilder(ProcessorContext context, Builder builderTypeBuilder) {
			extendConcludingBuilder(context, builderTypeBuilder, this.concludingBuilder);
		}

		private void returnNewConcludingBuilder(ProcessorContext context,
				MethodSpec.Builder setterBuilder, DeclaredType concludingBuilder) {
			setterBuilder.addCode("return new $T($L, $L);", concludingBuilder, "bundle()",
					"targetClass()");
			setterBuilder.returns(TypeName.get(concludingBuilder));
		}

	}

	private interface BuilderCodeGenerator {
		void build(PartialModel model, Builder builderTypeBuilder,
				SimpleBundleContext bundleContext, List<FieldModel> fields);
	}

	private class ChainedBuilderGenerator implements BuilderCodeGenerator {

		@Override
		public void build(PartialModel model, Builder builderTypeBuilder,
				SimpleBundleContext bundleContext, List<FieldModel> fields) {
			// we inherit the build method
			builderTypeBuilder.superclass(model.concludingBuilderTypeName());
			builderTypeBuilder.addMethod(MethodSpec.constructorBuilder()
					.addCode("super($L.class);", model.targetClassName).build());

			for (FieldModel field : fields) {
				Arg arg = field.annotation(Arg.class).get();
				Element<TypeMirror> element = new Element<>(field);
				String setterName = arg.value();
				if (Strings.isNullOrEmpty(setterName)) {
					setterName = field.name();
					// TODO allow and apply field name transform here
				}
				CascadingTypeAnalyzer<?, ? extends TypeMirror, ? extends Analysis> analyzer = resolver
						.resolve(element);
				Analysis analysis = analyzer.transform(bundleContext, element, InvocationType.SAVE);
				ClassName builderName = ClassName.get(model.builderFqpn, model.builderSimpleName);
				MethodSpec.Builder setterBuilder = MethodSpec.methodBuilder(setterName)
						.addModifiers(Modifier.PUBLIC)
						.addParameter(ClassName.get(field.type()), setterName)
						.addCode(analysis.emit());
				setBuilderReturnSpec(builderName, setterBuilder);
				builderTypeBuilder.addMethod(setterBuilder.build());
			}
		}

		public void setBuilderReturnSpec(ClassName self, MethodSpec.Builder builder) {
			builder.returns(self).addCode("return this;");
		}
	}

	private class VoidBuilderGenerator extends ChainedBuilderGenerator {

		@Override
		public void setBuilderReturnSpec(ClassName self, MethodSpec.Builder builder) {
			builder.returns(TypeName.VOID);
		}
	}

	private class SubclassBuilderGenerator implements BuilderCodeGenerator {

		@Override
		public void build(PartialModel model, Builder builderTypeBuilder,
				SimpleBundleContext bundleContext, List<FieldModel> fields) {

			// last field returns a concluding builder
			TypeName previousName = model.concludingBuilderTypeName();

			// last to first
			int last = fields.size() - 1;
			for (int i = last; i >= 0; i--) {
				FieldModel field = fields.get(i);
				Arg arg = field.annotation(Arg.class).get();

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
						.addParameter(ClassName.get(field.type()), setterName)
						.addCode(analysis.emit()).returns(previousName);

				if (i == 0) {
					// entry point is a method
					if (i == last) {
						setterBuilder.addCode("return new $T($L, $L);", previousName, "bundle()",
								"targetClass()");
					} else {
						setterBuilder.addCode("return new $T();", previousName);

					}

					builderTypeBuilder.addMethod(setterBuilder.build());

					// we're almost done
					// if the first field is optional, the whole builder can be
					// concluded without any setter, otherwise, implement our
					// base arg builder
					builderTypeBuilder.superclass(arg.optional() ? model.concludingBuilderTypeName()
							: ParameterizedTypeName.get(
									ClassName.get(Internal.ClassArgBuilder.class),
									model.targetClassName));

					model.extendConcludingBuilder(context, builderTypeBuilder);

				} else {
					// start chaining
					Builder builder = TypeSpec
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
											model.builderSimpleName, "bundle()", "targetClass()")
									.build());
							setterBuilder.addCode("return this;");
						} else {
							// last non optional field builder needs to return a
							// new
							// concluding builder
							setterBuilder.addCode("return new $T($L, $L);", previousName,
									"bundle()", "targetClass()");
						}

					} else {
						// everything in between returns the next builder
						setterBuilder.addCode("return new $T();", previousName);
					}

					builder.addMethod(setterBuilder.build());

					TypeSpec typeSpec = builder.build();
					builderTypeBuilder.addType(typeSpec);

					previousName = model.get(typeSpec.name);
				}
			}

		}
	}

}
