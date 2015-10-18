package com.sora.util.akatsuki;

import java.io.IOException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

import com.sora.util.akatsuki.BundleContext.SimpleBundleContext;
import com.sora.util.akatsuki.analyzers.CascadingTypeAnalyzer;
import com.sora.util.akatsuki.analyzers.CascadingTypeAnalyzer.Analysis;
import com.sora.util.akatsuki.analyzers.CascadingTypeAnalyzer.InvocationType;
import com.sora.util.akatsuki.analyzers.Element;
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

public class BundleCodeGenerator implements CodeGenerator<TypeSpec> {

	public ClassInfo generatedClassInfo() {
		return generatedClassInfo;
	}

	enum Action {
		SAVE("save", InvocationType.SAVE), RESTORE("restore", InvocationType.RESTORE);

		final String methodName;
		final InvocationType type;

		Action(String methodName, InvocationType type) {
			this.methodName = methodName;
			this.type = type;
		}
	}

	private final ClassInfo generatedClassInfo;
	private final ProcessorContext context;
	private final SourceClassModel classModel;
	private final TypeAnalyzerResolver resolver;
	private final Optional<Predicate<FieldModel>> fieldModelPredicate;
	private final EnumSet<Action> actions;
	private final Optional<FieldTransformation> fieldTransformation;

	BundleCodeGenerator(ProcessorContext context, SourceClassModel classModel,
			TypeAnalyzerResolver resolver, Optional<Predicate<FieldModel>> fieldModelPredicate,
			EnumSet<Action> action, Optional<FieldTransformation> fieldTransformation) {
		this.context = context;
		this.classModel = classModel;
		this.resolver = resolver;
		this.fieldModelPredicate = fieldModelPredicate;
		this.actions = action;
		this.fieldTransformation = fieldTransformation;
		this.generatedClassInfo = classModel.asClassInfo().transform(null,
				Internal::generateRetainerClassName);
	}

	@Override
	public TypeSpec createModel() {
		final String sourceName = "source";
		final String bundleName = "bundle";

		final SimpleBundleContext bundleContext = new SimpleBundleContext(sourceName, bundleName);

		final ClassName sourceClassName = ClassName.get(classModel.originatingElement());

		TypeVariableName actualClassCapture = TypeVariableName.get("T", sourceClassName);

		final ParameterSpec sourceSpec = ParameterSpec
				.builder(actualClassCapture, sourceName, Modifier.FINAL).build();

		final ParameterSpec bundleSpec = ParameterSpec
				.builder(ClassName.get(AndroidTypes.Bundle.asMirror(context)), bundleName,
						Modifier.FINAL)
				.build();

		EnumMap<Action, Builder> actionBuilderMap = new EnumMap<>(Action.class);

		// we implement the interface here, all methods action must actually be
		// there
		for (Action action : Action.values()) {
			final Builder saveMethodBuilder = MethodSpec.methodBuilder(action.methodName)
					.addModifiers(Modifier.PUBLIC).returns(void.class).addParameter(sourceSpec)
					.addParameter(bundleSpec);
			actionBuilderMap.put(action, saveMethodBuilder);
			if (classModel.directSuperModel().isPresent()) {
				String superInvocation = "super.$L($L, $L)";
				saveMethodBuilder.addStatement(superInvocation, action.methodName, sourceName,
						bundleName);
			}
		}

		List<Element<TypeMirror>> elements = classModel.fields().stream().map(Element::new)
				.collect(Collectors.toList());

		EnumSet<Action> emptyActions = EnumSet.complementOf(this.actions);

		for (Action action : emptyActions) {
			actionBuilderMap.get(action).addCode("throw new $T($S);", AssertionError.class,
					"Unused action, should not be called at all");
		}

		for (Element<TypeMirror> element : elements) {
			if (!fieldModelPredicate.orElseGet(() -> fm -> true).test(element.model()))
				continue;

			final CascadingTypeAnalyzer<?, ?, ?> strategy = resolver.resolve(element);
			if (strategy == null) {
				context.messager().printMessage(Kind.ERROR,
						"unsupported field, reflected type is " + element.refinedMirror()
								+ " representing class is " + element.refinedMirror().getClass(),
						element.originatingElement());
			} else {
				try {
					for (Action action : actions) {
						Analysis analysis = strategy.transform(bundleContext, element, action.type);
						fieldTransformation
								.ifPresent(ft -> ft.transform(context, action, element, analysis));
						actionBuilderMap.get(action)
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

		TypeSpec.Builder typeSpecBuilder = TypeSpec.classBuilder(generatedClassInfo.className)
				.addModifiers(Modifier.PUBLIC).addTypeVariable(actualClassCapture);

		for (Builder builder : actionBuilderMap.values()) {
			typeSpecBuilder.addMethod(builder.build());
		}

		Optional<SourceClassModel> superModel = classModel.directSuperModel();

		if (superModel.isPresent()) {

			ClassInfo superClassModel = superModel.get().asClassInfo().transform(null,
					Internal::generateRetainerClassName);

			final ClassName className = ClassName.get(superClassModel.fullyQualifiedPackageName,
					superClassModel.className);

			typeSpecBuilder
					.superclass(ParameterizedTypeName.get(className, TypeVariableName.get("T")));
		} else {
			final ParameterizedTypeName interfaceName = ParameterizedTypeName
					.get(ClassName.get(BundleRetainer.class), TypeVariableName.get("T"));
			typeSpecBuilder.addSuperinterface(interfaceName);
		}
		return typeSpecBuilder.build();
	}

	@Override
	public void writeSourceToFile(Filer filer) throws IOException {
		throw new RuntimeException("BundleCodeGenerator cannot be written to a file directly!");
	}

	public interface FieldTransformation {
		void transform(ProcessorContext context, Action action, Element<?> element,
				Analysis analysis);
	}

}
