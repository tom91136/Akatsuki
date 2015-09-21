package com.sora.util.akatsuki.models;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

import com.sora.util.akatsuki.Log;
import com.sora.util.akatsuki.ProcessorContext;

public class SourceTreeModel extends BaseModel {

	private static final Set<Modifier> DISALLOWED_MODIFIERS = EnumSet.of(Modifier.FINAL,
			Modifier.STATIC, Modifier.PRIVATE);
	private final List<SourceClassModel> models;

	private SourceTreeModel(ProcessorContext context, List<SourceClassModel> models) {
		super(context);
		this.models = models;
	}



	private static void collectElements(ArrayList<Element> elements, Element root, Set<Class<? extends Annotation>> classes){
		if(root.getKind()== ElementKind.CLASS){
			for (Element element : root.getEnclosedElements()) {
				collectElements(elements, element, classes);
			}
		}else if(root.getKind() == ElementKind.FIELD && classes.stream().anyMatch(c -> root.getAnnotation(c) != null)){
			elements.add(root);
		}
	}

	// create our model here
	public static SourceTreeModel fromRound(ProcessorContext context, RoundEnvironment roundEnv,
			Set<Class<? extends Annotation>> classes) {

//		final Set<Element> elements = new HashSet<>();
//		for (Class<? extends Annotation> clazz : classes) {
//			elements.addAll(roundEnv.getElementsAnnotatedWith(clazz));
//		}


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

				// transient marks the field as skipped
				if (!element.getModifiers().contains(Modifier.TRANSIENT)) {
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

		if (processed != list.size()) {
			context.messager().printMessage(Kind.NOTE,
					(list.size() - processed)
							+ " error(s) occurred, no files are generated after the first "
							+ "error has occurred.");
		} else {
			// stage 2, find superclass and mark hidden fields
			for (SourceClassModel model : classNameMap.values()) {
				model.findSuperClass(classNameMap);
				model.markHiddenFields();
			}
		}

		return verifyOnly ? null
				: new SourceTreeModel(context, new ArrayList<>(classNameMap.values()));
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

	public List<SourceClassModel> classModels() {
		return Collections.unmodifiableList(models);
	}

	// TODO make this concurrent; currently depending on thread safety of Types
	// and Elements
	public <M> List<M> forEachClassSerial(ClassWalker<M> walker,
			BiConsumer<Exception, SourceClassModel> exceptionHandler) {
		AtomicBoolean exceptionThrown = new AtomicBoolean();
		List<M> createdModels = classModels().stream().map(m -> {
			try {
				return walker.walk(m, this);
			} catch (Exception e) {
				exceptionHandler.accept(e, m);
				exceptionThrown.set(true);
				return null;
			}
		}).collect(Collectors.toList());
		return exceptionThrown.get() ? null : createdModels;
	}

	public interface ClassWalker<M> {
		M walk(SourceClassModel classModel, SourceTreeModel treeModel) throws Exception;
	}

	public SourceClassModel findModelWithAssignableMirror(TypeMirror mirror) {
		return models.stream().filter(model -> model.fullyQualifiedName().equals(mirror.toString()))
				.findFirst()
				.orElseGet(() -> models.stream()
						.filter(m -> context.utils().isAssignable(mirror, m.mirror(), true))
						.findFirst().orElse(null));
	}

}
