package com.sora.util.akatsuki.compiler;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

import com.sora.util.akatsuki.BundleRetainer;
import com.sora.util.akatsuki.Internal;
import com.sora.util.akatsuki.Retained;
import com.sora.util.akatsuki.Retained.RestorePolicy;
import com.sora.util.akatsuki.TransformationTemplate;
import com.sora.util.akatsuki.compiler.BundleContext.SimpleBundleContext;
import com.sora.util.akatsuki.compiler.analyzers.CascadingTypeAnalyzer;
import com.sora.util.akatsuki.compiler.analyzers.CascadingTypeAnalyzer.Analysis;
import com.sora.util.akatsuki.compiler.analyzers.CascadingTypeAnalyzer.InvocationType;
import com.sora.util.akatsuki.compiler.analyzers.Element;
import com.sora.util.akatsuki.compiler.models.ClassInfo;
import com.sora.util.akatsuki.compiler.models.GenerationTargetModel;
import com.sora.util.akatsuki.compiler.models.SourceClassModel;
import com.sora.util.akatsuki.compiler.models.SourceTreeModel;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.MethodSpec.Builder;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

public class BundleRetainerModel extends GenerationTargetModel {

	private final ClassInfo generatedClassInfo;
	private final TypeAnalyzerResolver resolver;

	BundleRetainerModel(ProcessorContext context, SourceClassModel classModel,
			SourceTreeModel treeModel, TypeAnalyzerResolver resolver) {
		super(context, classModel, treeModel);
		this.resolver = resolver;
		this.generatedClassInfo = classModel().asClassInfo().transform(null,
				Internal::generateRetainerClassName);
	}

	public ClassInfo generatedClassInfo() {
		return generatedClassInfo;
	}

	@Override
	public void writeSourceToFile(Filer filer) throws IOException {
		String sourceName = "source";
		String bundleName = "bundle";

		final SimpleBundleContext bundleContext = new SimpleBundleContext(sourceName, bundleName);

		final ClassName sourceClassName = ClassName.get(classModel().originatingElement());

		TypeVariableName actualClassCapture = TypeVariableName.get("T", sourceClassName);

		final ParameterSpec sourceSpec = ParameterSpec
				.builder(actualClassCapture, sourceName, Modifier.FINAL).build();

		final ParameterSpec bundleSpec = ParameterSpec
				.builder(ClassName.get(AndroidTypes.Bundle.asMirror(context)), bundleName,
						Modifier.FINAL)
				.build();

		final Builder saveMethodBuilder = MethodSpec.methodBuilder("save")
				.addModifiers(Modifier.PUBLIC).returns(void.class).addParameter(sourceSpec)
				.addParameter(bundleSpec);

		final Builder restoreMethodBuilder = MethodSpec.methodBuilder("restore")
				.addModifiers(Modifier.PUBLIC).returns(void.class).addParameter(sourceSpec)
				.addParameter(bundleSpec);

		if (classModel().directSuperModel() != null) {
			String superInvocation = "super.$L($L, $L)";
			saveMethodBuilder.addStatement(superInvocation, "save", sourceName, bundleName);
			restoreMethodBuilder.addStatement(superInvocation, "restore", sourceName, bundleName);
		}

		List<Element<TypeMirror>> elements = classModel().fields().stream().map(Element::new)
				.collect(Collectors.toList());

		for (Element<TypeMirror> element : elements) {
			Retained retained = element.model().annotation(Retained.class);
			if (retained == null || retained.skip())
				continue;
			final CascadingTypeAnalyzer<?, ?, ?> strategy = resolver.resolve(element);
			if (strategy == null) {
				context.messager().printMessage(Kind.ERROR,
						"unsupported field, reflected type is " + "" + "" + ""
								+ element.refinedMirror() + "" + " representing class is "
								+ element.refinedMirror().getClass(),
						element.originatingElement());
			} else {
				try {
					final Analysis save = strategy.transform(bundleContext, element,
							InvocationType.SAVE);
					final Analysis restore = strategy.transform(bundleContext, element,
							InvocationType.RESTORE);

					RestorePolicy policy = retained.restorePolicy();
					if (policy == RestorePolicy.DEFAULT) {
						policy = AkatsukiProcessor.retainConfig().restorePolicy();
					}
					// policy only works on objects as primitives have default
					// values which we can't really check for :(
					if (!context.utils().isPrimitive(element.fieldMirror())) {
						switch (policy) {
						case IF_NULL:
							restore.wrap(s -> "if({{fieldName}} == null){\n" + s + "}\n");
							break;
						case IF_NOT_NULL:
							restore.wrap(s -> "if({{fieldName}} != null){\n" + s + "}\n");
							break;
						default:
						case DEFAULT:
						case OVERWRITE:
							// do nothing
							break;
						}
					}
					saveMethodBuilder.addCode(JavaPoetUtils.escapeStatement(
							save.preEmitOnce() + save.emit() + save.postEmitOnce()));
					restoreMethodBuilder.addCode(JavaPoetUtils.escapeStatement(
							restore.preEmitOnce() + restore.emit() + restore.postEmitOnce()));
				} catch (Exception | Error e) {
					context.messager().printMessage(Kind.ERROR, "An exception/error occurred",
							element.originatingElement());
					throw new RuntimeException(e);
				}
			}

		}

		TypeSpec.Builder typeSpecBuilder = TypeSpec.classBuilder(generatedClassInfo.className)
				.addModifiers(Modifier.PUBLIC).addMethod(saveMethodBuilder.build())
				.addMethod(restoreMethodBuilder.build()).addTypeVariable(actualClassCapture);

		if (classModel().directSuperModel() != null) {

			ClassInfo superClassModel = classModel().directSuperModel().asClassInfo()
					.transform(null, Internal::generateRetainerClassName);

			final ClassName className = ClassName.get(superClassModel.fullyQualifiedPackageName,
					superClassModel.className);

			typeSpecBuilder
					.superclass(ParameterizedTypeName.get(className, TypeVariableName.get("T")));
		} else {
			final ParameterizedTypeName interfaceName = ParameterizedTypeName
					.get(ClassName.get(BundleRetainer.class), TypeVariableName.get("T"));
			typeSpecBuilder.addSuperinterface(interfaceName);
		}

		JavaFile javaFile = JavaFile
				.builder(generatedClassInfo.fullyQualifiedPackageName, typeSpecBuilder.build())
				.build();

		javaFile.writeTo(filer);
	}

}
