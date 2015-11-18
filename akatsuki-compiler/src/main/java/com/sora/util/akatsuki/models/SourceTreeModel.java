package com.sora.util.akatsuki.models;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

import com.google.common.base.MoreObjects;
import com.sora.util.akatsuki.Log;
import com.sora.util.akatsuki.ProcessorContext;

public class SourceTreeModel extends BaseModel {

	private static final Set<Modifier> DISALLOWED_MODIFIERS = EnumSet.of(Modifier.FINAL,
			Modifier.STATIC, Modifier.PRIVATE);

	private final List<SourceClassModel> models;
	private final List<SourceClassModel> flattenedModel;

	public enum Arrangement {
		FLATTENED, HEAD
	}

	private SourceTreeModel(ProcessorContext context, List<SourceClassModel> models) {
		super(context);
		this.models = models;
		this.flattenedModel = models.stream()
				.flatMap(m -> Stream.concat(m.children().stream(), Stream.of(m))).distinct()
				.collect(Collectors.toList());
	}

	public boolean containsClass(CharSequence fqcn) {
		return models.stream().anyMatch(m -> m.fullyQualifiedName().equals(fqcn));
	}

	private static void collectElements(ArrayList<Element> elements, Element root,
			Set<Class<? extends Annotation>> classes) {
		if (root.getKind() == ElementKind.CLASS) {
			for (Element element : root.getEnclosedElements()) {
				collectElements(elements, element, classes);
			}
		} else if (root.getKind() == ElementKind.FIELD
				&& classes.stream().anyMatch(c -> root.getAnnotation(c) != null)) {
			elements.add(root);
		}
	}

	// create our model here
	public static SourceTreeModel fromRound(ProcessorContext context, RoundEnvironment roundEnv,
			Set<Class<? extends Annotation>> classes) {

		// final Set<Element> elements = new HashSet<>();
		// for (Class<? extends Annotation> clazz : classes) {
		// elements.addAll(roundEnv.getElementsAnnotatedWith(clazz));
		// }

		ArrayList<Element> list = new ArrayList<>();
		for (Element root : roundEnv.getRootElements()) {
			collectElements(list, root, classes);
		}

		Map<String, SourceClassModel> classNameMap = new HashMap<>();
		int processed = 0;
		boolean verifyOnly = false;
		for (Element element : list) {

			// skip if error
			if (!annotatedElementValid(context, element)
					|| !enclosingClassValid(context, element)) {
				verifyOnly = true;
				continue;
			}

			// >= 1 error has occurred, we're in verify mode
			if (!verifyOnly) {
				final TypeElement enclosingClass = (TypeElement) element.getEnclosingElement();

				if (context.config().fieldAllowed(element)) {
					final SourceClassModel model = classNameMap.computeIfAbsent(
							enclosingClass.getQualifiedName().toString(),
							k -> new SourceClassModel(context, enclosingClass));
					Set<Class<? extends Annotation>> annotationClasses = classes.stream()
							.filter(c -> element.getAnnotation(c) != null)
							.collect(Collectors.toSet());
					model.fields.add(new FieldModel((VariableElement) element, annotationClasses));
					Log.verbose(context, "Element marked", element);
				} else {
					Log.verbose(context, "Element skipped", element);
				}
			}
			processed++;
		}

		Collection<SourceClassModel> models = classNameMap.values();
		if (processed != list.size()) {
			context.messager().printMessage(Kind.NOTE,
					(list.size() - processed)
							+ " error(s) occurred, no files are generated after the first "
							+ "error has occurred.");
		} else {
			// stage 2, initialize them all
			models.forEach(model -> model.linkParent(classNameMap));
			models.forEach(model -> model.findChildren(classNameMap, roundEnv));

			models.forEach(SourceClassModel::markHiddenFields);
		}

		return verifyOnly ? null : new SourceTreeModel(context, new ArrayList<>(models));
	}

	private static boolean annotatedElementValid(ProcessorContext context, Element element) {
		// sanity check
		if (!element.getKind().equals(ElementKind.FIELD)) {
			context.messager().printMessage(Kind.ERROR, "annotated target must be a field",
					element);
			return false;
		}

		// more sanity check
		if (!(element instanceof VariableElement)) {
			context.messager().printMessage(Kind.ERROR,
					"Element is not a variable?! (should not happen at all) ", element);
			return false;
		}

		// check for invalid modifiers, we can only create classes in the
		// same package
		if (!Collections.disjoint(element.getModifiers(), DISALLOWED_MODIFIERS)) {
			context.messager().printMessage(Kind.ERROR,
					"field with " + DISALLOWED_MODIFIERS.toString() + " " + "cannot be retained",
					element);
			return false;
		}

		return true;
	}

	private static boolean enclosingClassValid(ProcessorContext context, Element element) {
		Element enclosingElement = element.getEnclosingElement();
		while (enclosingElement != null) {
			// skip until we find a class
			if (!enclosingElement.getKind().equals(ElementKind.CLASS))
				break;

			if (!enclosingElement.getKind().equals(ElementKind.CLASS)) {
				context.messager().printMessage(Kind.ERROR,
						"enclosing element(" + enclosingElement.toString() + ") is not a class",
						element);
				return false;
			}

			TypeElement enclosingClass = (TypeElement) enclosingElement;

			// protected, package-private, and public all allow same package
			// access
			if (enclosingClass.getModifiers().contains(Modifier.PRIVATE)) {
				context.messager().printMessage(Kind.ERROR,
						"enclosing class (" + enclosingElement.toString() + ") cannot be private",
						element);
				return false;
			}

			if (enclosingClass.getNestingKind() != NestingKind.TOP_LEVEL
					&& !enclosingClass.getModifiers().contains(Modifier.STATIC)) {
				context.messager().printMessage(Kind.ERROR,
						"enclosing class is nested but not static", element);
				return false;
			}
			enclosingElement = enclosingClass.getEnclosingElement();
		}

		return true;
	}

	static boolean enclosingClassValid(ProcessorContext context, TypeElement enclosingClass){

		// protected, package-private, and public all allow same package
		// access
		if (enclosingClass.getModifiers().contains(Modifier.PRIVATE)) {
			context.messager().printMessage(Kind.ERROR,
					"class cannot be private",
					enclosingClass);
			return false;
		}

		if (enclosingClass.getNestingKind() != NestingKind.TOP_LEVEL
				    && !enclosingClass.getModifiers().contains(Modifier.STATIC)) {
			context.messager().printMessage(Kind.ERROR,
					"class is nested but not static", enclosingClass);
			return false;
		}
		return true;
	}

	public List<SourceClassModel> classModels(Arrangement arrangement) {
		return Collections
				.unmodifiableList(arrangement == Arrangement.FLATTENED ? flattenedModel : models);
	}

	public Collection<SourceClassModel> findModelWithMatchingElement(TypeElement e) {
		return models.stream().filter(classModel -> classModel.originatingElement().equals(e))
				.collect(Collectors.toSet());
	}

	public SourceClassModel findModelWithAssignableMirror(TypeMirror mirror) {
		return models.stream().filter(model -> model.fullyQualifiedName().equals(mirror.toString()))
				.findFirst()
				.orElseGet(() -> models.stream()
						.filter(m -> context.utils().isAssignable(mirror, m.mirror(), true))
						.findFirst().orElse(null));
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("models", models).toString();
	}
}
