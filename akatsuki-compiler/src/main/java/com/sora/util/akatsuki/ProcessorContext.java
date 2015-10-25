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
	private boolean round;

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

		if (this.config != null)
			throw new IllegalStateException("Configuration cannot be changed once set!");
		this.config = config;
	}

	public void roundStarted(){
		this.round = true;
		this.config = null;
	}

	public void roundFinished(){
		this.round = false;
		Log.verbose(this, "Round finished");
		this.config = null;
	}

	public Configuration config() {
		return config;
	}

}
