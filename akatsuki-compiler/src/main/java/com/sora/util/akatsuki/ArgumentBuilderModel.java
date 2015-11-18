package com.sora.util.akatsuki;

import static com.sora.util.akatsuki.SourceUtils.T;
import static com.sora.util.akatsuki.SourceUtils.var;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
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
import com.sora.util.akatsuki.ArgConfig.BuilderType.Check;
import com.sora.util.akatsuki.ArgConfig.Order;
import com.sora.util.akatsuki.ArgConfig.Sort;
import com.sora.util.akatsuki.BundleContext.SimpleBundleContext;
import com.sora.util.akatsuki.BundleRetainerClassBuilder.Direction;
import com.sora.util.akatsuki.Internal.ClassArgBuilder;
import com.sora.util.akatsuki.analyzers.CascadingTypeAnalyzer;
import com.sora.util.akatsuki.analyzers.CascadingTypeAnalyzer.Analysis;
import com.sora.util.akatsuki.analyzers.CascadingTypeAnalyzer.InvocationType;
import com.sora.util.akatsuki.analyzers.Element;
import com.sora.util.akatsuki.models.ClassInfo;
import com.sora.util.akatsuki.models.FieldModel;
import com.sora.util.akatsuki.models.SourceClassModel;
import com.sora.util.akatsuki.models.SourceMappingModel;
import com.sora.util.akatsuki.models.SourceTreeModel;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeSpec.Builder;
import com.squareup.javapoet.TypeVariableName;

// Spec: every package has it's own Builder...
class ArgumentBuilderModel extends SourceMappingModel {

	public static final Function<String, String> BUILDER_NAME_FUNCTION = n -> n
			+ Internal.BUILDER_CLASS_SUFFIX;

	private final Map<TypeMirror, DeclaredType> SUPPORTED_TYPES = new HashMap<>();
	private final TypeName bundleTypeName;
	private final ClassInfo info;
	private final Optional<String> enclosingClass;

	protected ArgumentBuilderModel(ProcessorContext context, SourceClassModel classModel,
			SourceTreeModel treeModel, Optional<String> enclosingClass) {
		super(context, classModel, treeModel);
		bundleTypeName = ClassName.get(AndroidTypes.Bundle.asMirror(context));
		initializeSupportedTypes();
		this.enclosingClass = enclosingClass;
		ClassInfo info = classModel.asClassInfo().withNameTransform(BUILDER_NAME_FUNCTION);
		this.info = enclosingClass.isPresent() ? info.withEnclosingClasses(enclosingClass.get())
				: info;
	}

	// TODO this is bad (we cannot cache mirrors across rounds, they explode!)
	private void initializeSupportedTypes() {
		if (SUPPORTED_TYPES.isEmpty()) {
			SUPPORTED_TYPES.put(mirror("android.app.Fragment"),
					mirror(FragmentConcludingBuilder.class));
			SUPPORTED_TYPES.put(mirror("android.support.v4.app.Fragment"),
					mirror(FragmentConcludingBuilder.class));
			SUPPORTED_TYPES.put(mirror("android.app.Activity"),
					mirror(ActivityConcludingBuilder.class));
			SUPPORTED_TYPES.put(mirror("android.app.Service"),
					mirror(ServiceConcludingBuilder.class));
		}
	}

	private DeclaredType mirror(Class<?> clazz) {
		return (DeclaredType) context.utils().of(clazz);
	}

	private DeclaredType mirror(String className) {
		return (DeclaredType) context.utils().of(className);
	}

	public Optional<TypeSpec> build() {
		Optional<SourceClassModel> superModel = classModel()
				.directSuperModelWithAnnotation(Arg.class);
		if (!classModel().containsAnyAnnotation(Arg.class) && !superModel.isPresent())
			return Optional.empty();
		return createBuilderForModel(classModel(), superModel).map(Builder::build);
	}

	public ClassInfo classInfo() {
		return info;
	}

	@Override
	public void writeToFile(Filer filer) throws IOException {
		if (enclosingClass.isPresent())
			throw new RuntimeException(
					"Enclosing class exists, please write the enclosing class instead of this inner class");
		throw new UnsupportedOperationException("not implemented yet");

	}

	// private ClassName createBuilderClassName(String modelFqpn, String
	// builderName) {
	// return ClassName.get(modelFqpn, enclosingClassName, builderName);
	// }

	private Optional<TypeSpec.Builder> createBuilderForModel(SourceClassModel model,
			Optional<SourceClassModel> superModel) {

		// get the config, if none, copy the config from parent
		ArgConfig config = model.annotation(ArgConfig.class).orElse(
				superModel.map(sm -> sm.annotation(ArgConfig.class).orElse(null)).orElse(null));

		// if still none, use the global default
		if (config == null) {
			config = context.config().argConfig();
		}

		if (!config.enabled())
			return Optional.empty();

		// our target type's class
		ClassName targetTypeName = ClassName.get(model.originatingElement());

		// generate builder's name

		final TypeSpec.Builder builderTypeBuilder = TypeSpec.classBuilder(info.className)
				.addModifiers(Modifier.PUBLIC, Modifier.STATIC);

		SimpleBundleContext bundleContext = new SimpleBundleContext("", "bundle");

		// filter, sort, and order our fields
		Predicate<FieldModel> modelPredicate = fm -> fm.annotation(Arg.class).isPresent()
				&& !fm.annotation(Arg.class).get().skip();
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

		BundleRetainerClassBuilder retainerClassBuilder = new BundleRetainerClassBuilder(context,
				model, EnumSet.of(Direction.RESTORE), classInfo -> {

					ClassInfo info = classInfo.withNameTransform(name -> Internal
							.generateRetainerClassName(name + Internal.BUILDER_CLASS_SUFFIX));
					if (enclosingClass.isPresent())
						info = info.withEnclosingClasses(enclosingClass.get());

					info = info.withEnclosingClasses(classInfo().className);
					return info;
				} , classInfo -> {
					String className = classInfo.className;
					ClassInfo info = classInfo.withNameTransform(name -> Internal
							.generateRetainerClassName(name + Internal.BUILDER_CLASS_SUFFIX));
					if (enclosingClass.isPresent())
						info = info.withEnclosingClasses(enclosingClass.get());
					info = info.withEnclosingClasses(className + Internal.BUILDER_CLASS_SUFFIX);
					return info;
				}).withFieldPredicate(modelPredicate);

		builderTypeBuilder
				.addType(retainerClassBuilder.build().addModifiers(Modifier.STATIC).build());

		if (config.order() == Order.DSC)
			Collections.reverse(fields);

		final Optional<DeclaredType> possibleConcluderType = context.utils()
				.getClassFromAnnotationMethod(config::concludingBuilder, VoidBuilder.class);

		DeclaredType concluderType = possibleConcluderType.orElse(SUPPORTED_TYPES.keySet().stream()
				.filter(m -> context.utils().isAssignable(model.mirror(), m, true)).findFirst()
				.map(SUPPORTED_TYPES::get)
				.orElseThrow(() -> new RuntimeException(model.fullyQualifiedName()
						+ " is not supported directly(detected as " + model.mirror() + ")."
						+ " @Arg supports Fragment, Activity and Service natively,"
						+ " you may set specify a concludingBuilder in @ArgConfig "
						+ "to describe how this class should be built.\n"
						+ Utils.listSuperclass(model.mirror()))));

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

		generator.build(
				new PartialModel(config, model.fullyQualifiedPackageName(), info.className,
						targetTypeName, concluderType),
				builderTypeBuilder, bundleContext, fields, superModel);
		return Optional.of(builderTypeBuilder);
	}

	private static class PartialModel {

		final ArgConfig config;
		final String builderFqpn;
		final String builderSimpleName;
		final ClassName targetClassName;
		final DeclaredType concludingBuilder;

		public PartialModel(ArgConfig config, String builderFqpn, String builderSimpleName,
				ClassName targetClassName, DeclaredType concludingBuilder) {

			this.config = config;
			this.builderFqpn = builderFqpn;
			this.builderSimpleName = builderSimpleName;
			this.targetClassName = targetClassName;
			this.concludingBuilder = concludingBuilder;
		}

		public ParameterizedTypeName concludingBuilderTypeName() {
			return ParameterizedTypeName.get(
					ClassName.get((TypeElement) concludingBuilder.asElement()),
					TypeVariableName.get("T"));
		}

		public ClassName get(String name) {
			return ClassName.get(builderFqpn, builderSimpleName, name);
		}

		private void extendConcludingBuilder(ProcessorContext context, Builder builderTypeBuilder,
				DeclaredType concludingBuilder) {
			if (context.utils().isAssignable(concludingBuilder,
					context.utils().of(ClassArgBuilder.class), true)) {
				// implement the constructor with class parameters
				// TODO that bundle part is redundant... consider improving
				builderTypeBuilder.addMethod(MethodSpec.constructorBuilder()
						.addParameter(ClassName.get(AndroidTypes.Bundle.asMirror(context)),
								"bundle")
						.addCode("super(bundle, $L.class);", targetClassName).build());
			} else if (context.utils().isAssignable(concludingBuilder,
					context.utils().of(ArgConcludingBuilder.class), true)) {
				// this is probably an user implemented one
				builderTypeBuilder.addMethod(MethodSpec.constructorBuilder()
						.addCode("this.bundle = new Bundle();").build());
			} else {
				throw new AssertionError(concludingBuilder + " is not supported, @ArgConfig should"
						+ " not allow this in the first place...");
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
				SimpleBundleContext bundleContext, List<FieldModel> fields,
				Optional<SourceClassModel> superClass);

	}

	private class ChainedBuilderGenerator implements BuilderCodeGenerator {

		@Override
		public void build(PartialModel model, Builder builderTypeBuilder,
				SimpleBundleContext bundleContext, List<FieldModel> fields,
				Optional<SourceClassModel> superClass) {

			// TODO what if out target class has some generic parameter of
			// <A,B...>?

			TypeName builderClassName = ParameterizedTypeName.get(info.toClassName(), T, var("BT"));

			builderTypeBuilder.addTypeVariable(TypeVariableName.get("T", model.targetClassName));

			builderTypeBuilder.addTypeVariable(TypeVariableName.get("BT", builderClassName));

			if (superClass.isPresent()) {
				// implement our parent

				ClassInfo superClassInfo = superClass.get().asClassInfo();
				if (enclosingClass.isPresent())
					superClassInfo = superClassInfo.withEnclosingClasses(enclosingClass.get());

				superClassInfo = superClassInfo.withNameTransform(BUILDER_NAME_FUNCTION);

				ClassName superClassName = superClassInfo.toClassName();

				builderTypeBuilder
						.superclass(ParameterizedTypeName.get(superClassName, T, var("BT")));
			} else {
				// or inherit the class containing our build method
				builderTypeBuilder.superclass(model.concludingBuilderTypeName());
			}

			builderTypeBuilder.addMethod(
					MethodSpec.constructorBuilder().addParameter(bundleTypeName, "bundle")
							.addCode("super(bundle, $L.class);", model.targetClassName).build());

			// if (!superClass.isPresent()) {
			// we are the first!
			builderTypeBuilder.addMethod(MethodSpec.constructorBuilder()
					.addParameter(bundleTypeName, "bundle").addParameter(Class.class, "clazz")
					.addCode("super(bundle, clazz);").addModifiers(Modifier.PUBLIC).build())
					.build();
			// }

			CodeBlock.Builder block = CodeBlock.builder();

			// enable check if runtime check is required and whether the class
			// contains non-optional fields at all
			boolean appendCheck = model.config.type().check == Check.RUNTIME
					&& fields.stream().anyMatch(f -> !f.annotation(Arg.class).get().optional());

			if (appendCheck) {
				block.addStatement("$T<$T> missing = new $T<>()", Set.class, String.class,
						HashSet.class);
			}

			for (FieldModel field : fields) {
				Arg arg = field.annotation(Arg.class).get();

				Element<TypeMirror> element = new Element<>(field);
				String setterName = arg.value();
				if (Strings.isNullOrEmpty(setterName)) {
					setterName = field.name();
					// TODO allow and apply field name transform here
				}
				CascadingTypeAnalyzer<?, ? extends TypeMirror, ? extends Analysis> analyzer = context
						.resolver().resolve(element);
				Analysis analysis = analyzer.transform(bundleContext, element, InvocationType.SAVE);

				MethodSpec.Builder setterBuilder = MethodSpec.methodBuilder(setterName)
						.addModifiers(Modifier.PUBLIC)
						.addParameter(ClassName.get(field.type()), setterName)
						.addCode(analysis.emit());
				setBuilderReturnSpec(var("BT"), setterBuilder);
				builderTypeBuilder.addMethod(setterBuilder.build());

				if (appendCheck && !arg.optional()) {
					block.addStatement("if(!bundle.containsKey($1S)) missing.add($1S)", setterName);
				}

			}

			if (appendCheck) {
				block.addStatement("if(missing.isEmpty()) throw new RuntimeException(\"Some or "
						+ "all of the non-optional values \"+missing+\" not set!\")");
				MethodSpec.Builder checkMethodBuilder = MethodSpec.methodBuilder("check")
						.addModifiers(Modifier.PUBLIC).returns(Void.TYPE).addCode(block.build());
				builderTypeBuilder.addMethod(checkMethodBuilder.build());
			}

		}

		public void setBuilderReturnSpec(TypeName self, MethodSpec.Builder builder) {
			builder.returns(self).addCode("return ($T)this;", self);
		}
	}

	private class VoidBuilderGenerator extends ChainedBuilderGenerator {

		@Override
		public void setBuilderReturnSpec(TypeName self, MethodSpec.Builder builder) {
			builder.returns(TypeName.VOID);
		}
	}

	private class SubclassBuilderGenerator implements BuilderCodeGenerator {

		@Override
		public void build(PartialModel model, Builder builderTypeBuilder,
				SimpleBundleContext bundleContext, List<FieldModel> fields,
				Optional<SourceClassModel> superClass) {

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

				CascadingTypeAnalyzer<?, ? extends TypeMirror, ? extends Analysis> analyzer = context
						.resolver().resolve(element);
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
									.addCode("super($1L" + ".this" + ".$2L, " + "$1L.this.$3L);",
											model.builderSimpleName, "bundle()", "targetClass()")
									.build());
							setterBuilder.addCode("return this;");
						} else {
							// last non optional field builder needs to return a
							// new
							// concluding builder
							setterBuilder.addCode("return new $T($L, $L);", previousName,
									"bundle" + "()", "targetClass()");
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
