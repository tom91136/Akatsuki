package com.sora.util.akatsuki;

import static com.sora.util.akatsuki.SourceUtils.T;
import static com.sora.util.akatsuki.SourceUtils.type;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

import com.sora.util.akatsuki.BundleContext.SimpleBundleContext;
import com.sora.util.akatsuki.analyzers.CascadingTypeAnalyzer;
import com.sora.util.akatsuki.analyzers.CascadingTypeAnalyzer.Analysis;
import com.sora.util.akatsuki.analyzers.CascadingTypeAnalyzer.InvocationType;
import com.sora.util.akatsuki.analyzers.Element;
import com.sora.util.akatsuki.models.BaseModel;
import com.sora.util.akatsuki.models.ClassInfo;
import com.sora.util.akatsuki.models.FieldModel;
import com.sora.util.akatsuki.models.SourceClassModel;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.MethodSpec.Builder;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

public class BundleRetainerClassBuilder extends BaseModel {

	enum Direction {
		SAVE("save", InvocationType.SAVE), RESTORE("restore", InvocationType.RESTORE);

		final String methodName;
		final InvocationType type;

		Direction(String methodName, InvocationType type) {
			this.methodName = methodName;
			this.type = type;
		}
	}

	private final SourceClassModel classModel;
	private final EnumSet<Direction> directions;
	private final Function<ClassInfo, ClassInfo> classInfoFunction;
	private final Function<ClassInfo, ClassInfo> superClassInfoFunction;

	private Optional<Predicate<FieldModel>> fieldModelPredicate = Optional.empty();
	private Optional<AnalysisTransformation> analysisTransformation = Optional.empty();
	private Optional<BundleContext> bundleContext = Optional.empty();

	BundleRetainerClassBuilder(ProcessorContext context, SourceClassModel classModel,
			EnumSet<Direction> direction, Function<ClassInfo, ClassInfo> classInfoFunction,
			Function<ClassInfo, ClassInfo> superClassInfoFunction) {
		super(context);
		this.classModel = classModel;
		this.directions = direction;
		this.classInfoFunction = classInfoFunction;
		this.superClassInfoFunction = superClassInfoFunction;
	}

	public BundleRetainerClassBuilder withFieldPredicate(Predicate<FieldModel> modelPredicate) {
		this.fieldModelPredicate = Optional.of(modelPredicate);
		return this;
	}

	public BundleRetainerClassBuilder withAnalysisTransformation(
			AnalysisTransformation transformation) {
		this.analysisTransformation = Optional.of(transformation);
		return this;
	}

	public BundleRetainerClassBuilder withBundleContext(BundleContext context) {
		this.bundleContext = Optional.of(context);
		return this;
	}

	// public ClassInfo classInfo() {
	// return classInfo;
	// }

	public TypeSpec.Builder build() {

		BundleContext bundleContext = this.bundleContext
				.orElse(new SimpleBundleContext("source", "bundle"));

		final ClassName sourceClassName = ClassName.get(classModel.originatingElement());

		TypeVariableName actualClassCapture = SourceUtils.T_extends(sourceClassName);

		final ParameterSpec sourceSpec = ParameterSpec
				.builder(actualClassCapture, bundleContext.sourceObjectName(), Modifier.FINAL)
				.build();

		final ParameterSpec bundleSpec = ParameterSpec
				.builder(ClassName.get(AndroidTypes.Bundle.asMirror(context)),
						bundleContext.bundleObjectName(), Modifier.FINAL)
				.build();

		EnumMap<Direction, Builder> actionBuilderMap = new EnumMap<>(Direction.class);

		// we implement the interface here, all methods action must actually be
		// there
		for (Direction direction : Direction.values()) {
			final Builder saveMethodBuilder = MethodSpec.methodBuilder(direction.methodName)
					.addModifiers(Modifier.PUBLIC).returns(void.class).addParameter(sourceSpec)
					.addParameter(bundleSpec);
			actionBuilderMap.put(direction, saveMethodBuilder);
			if (classModel.directSuperModel().isPresent()) {
				String superInvocation = "super.$L($L, $L)";
				saveMethodBuilder.addStatement(superInvocation, direction.methodName,
						bundleContext.sourceObjectName(), bundleContext.bundleObjectName());
			}
		}

		List<Element<TypeMirror>> elements = classModel.fields().stream().map(Element::new)
				.collect(Collectors.toList());

		EnumSet<Direction> emptyDirections = EnumSet.complementOf(this.directions);

		for (Direction direction : emptyDirections) {
			actionBuilderMap.get(direction).addCode("throw new $T($S);", AssertionError.class,
					"Unused action, should not be called at all");
		}

		for (Element<TypeMirror> element : elements) {
			if (!fieldModelPredicate.orElseGet(() -> fm -> true).test(element.model()))
				continue;

			final CascadingTypeAnalyzer<?, ?, ?> strategy = context.resolver().resolve(element);
			if (strategy == null) {
				context.messager().printMessage(Kind.ERROR,
						"unsupported field, reflected type is " + element.refinedMirror()
								+ " representing class is " + element.refinedMirror().getClass(),
						element.originatingElement());
			} else {
				try {
					for (Direction direction : directions) {
						Analysis analysis = strategy.transform(bundleContext, element,
								direction.type);
						analysisTransformation.ifPresent(
								ft -> ft.transform(context, direction, element, analysis));
						actionBuilderMap.get(direction)
								.addCode(JavaPoetUtils.escapeStatement(analysis.preEmitOnce()
										+ analysis.emit() + analysis.postEmitOnce()));
					}
				} catch (Exception | Error e) {
					context.messager().printMessage(Kind.ERROR, "An exception/error occurred",
							element.originatingElement());
					throw new RuntimeException(e);
				}
			}
		}

		TypeSpec.Builder typeSpecBuilder = TypeSpec
				.classBuilder(classInfoFunction.apply(classModel.asClassInfo()).className)
				.addModifiers(Modifier.PUBLIC).addTypeVariable(actualClassCapture);

		for (Builder builder : actionBuilderMap.values()) {
			typeSpecBuilder.addMethod(builder.build());
		}

		Optional<SourceClassModel> superModel = classModel.directSuperModel();

		if (superModel.isPresent()) {
			ClassName className = superClassInfoFunction.apply(superModel.get().asClassInfo())
					.toClassName();
			typeSpecBuilder.superclass(type(className, T));
		} else {
			final ParameterizedTypeName interfaceName = type(BundleRetainer.class, T);
			typeSpecBuilder.addSuperinterface(interfaceName);
		}
		return typeSpecBuilder;
	}

	public interface AnalysisTransformation {
		void transform(ProcessorContext context, Direction direction, Element<?> element,
				Analysis analysis);
	}

}
