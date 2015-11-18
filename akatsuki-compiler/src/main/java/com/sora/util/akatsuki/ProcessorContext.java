package com.sora.util.akatsuki;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public class ProcessorContext {

	private final ProcessingEnvironment environment;
	private final Types types;
	private final Elements elements;
	private final ProcessorUtils utils;
	private Configuration config;
	private TypeAnalyzerResolver bundleTypeResolver;

	public ProcessorContext(ProcessingEnvironment environment) {
		this.environment = environment;
		this.types = environment.getTypeUtils();
		this.elements = environment.getElementUtils();
		this.utils = new ProcessorUtils(this.types, this.elements);

	}

	public ProcessorContext(ProcessorContext context) {
		this.environment = context.environment;
		this.types = context.types;
		this.elements = context.elements;
		this.utils = context.utils;
	}

	public Types types() {
		return types;
	}

	public Elements elements() {
		return elements;
	}

	public ProcessorUtils utils() {
		return utils;
	}

	public Messager messager() {
		return environment.getMessager();
	}

	void setConfigForRound(Configuration config) {
		checkState(this.config, "config");
		this.config = config;
	}

	public void setBundleTypeResolverForRound(TypeAnalyzerResolver bundleTypeResolver) {
		checkState(this.bundleTypeResolver, "resolver");
		this.bundleTypeResolver = bundleTypeResolver;
	}

	private void checkState(Object object, String name) {
		if (object != null)
			throw new IllegalStateException(name + " cannot be changed for round once set!");
	}

	public void roundStarted() {
		clearRoundState();
	}

	public void roundFinished() {
		clearRoundState();
		Log.verbose(this, "Round finished");
	}

	private void clearRoundState() {
		this.config = null;
		this.bundleTypeResolver = null;
	}

	public Configuration config() {
		return config;
	}

	public TypeAnalyzerResolver resolver() {
		return bundleTypeResolver;
	}

}
