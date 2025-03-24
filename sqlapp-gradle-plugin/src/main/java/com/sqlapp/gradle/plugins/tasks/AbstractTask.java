package com.sqlapp.gradle.plugins.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.ConsoleOutputLevel;

public abstract class AbstractTask extends DefaultTask {

	@Input
	@Optional
	public abstract Property<Boolean> getDebug();

	@Input
	@Optional
	public abstract MapProperty<String, Object> getParameters();

	@Input
	@Optional
	public abstract Property<String> getConsoleOutputLevel();

	@Internal
	protected void run(AbstractCommand command) {
		if (this.getParameters().isPresent()) {
			command.getContext().putAll(this.getParameters().get());
		}
		if (getDebug().getOrElse(false)) {
			System.out.println("parameters=" + this.getParameters().get());
		}
		if (getConsoleOutputLevel().isPresent()) {
			command.setConsoleOutputLevel(ConsoleOutputLevel.parse(getConsoleOutputLevel().get()));
		}
		if (this.getEnabled()) {
			try {
				command.run();
			} catch (Exception e) {
				e.printStackTrace();
				throw e;
			}
		} else {
			System.out.println("This task is disabled.");
		}
	}

}
