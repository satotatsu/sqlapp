package com.sqlapp.gradle.plugins.extension;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.DiffCommand;
import com.sqlapp.data.schemas.EqualsHandler;

public abstract class DiffSchemaXmlExtension extends AbstractExtension {
	@Inject
	public DiffSchemaXmlExtension(Project project) {
		super(project);
	}

	@Internal
	public void call(Action<DiffSchemaXmlExtension> cons) {
		cons.execute(this);
	}

	/**
	 * Output originalFilePath
	 */
	@InputFile
	public abstract RegularFileProperty getOriginalFile();

	/**
	 * Output targetFilePath
	 */
	@InputFile
	public abstract RegularFileProperty getTargetFile();

	@Input
	@Optional
	public abstract Property<EqualsHandler> getEqualsHandler();

	@Internal
	@Override
	public void setCommand(AbstractCommand command, boolean debug) {
		super.setCommand(command, debug);
		if (command instanceof DiffCommand) {
			DiffCommand com = (DiffCommand) command;
			com.setOriginalFile(getOriginalFile().getAsFile().get());
			com.setTargetFile(getTargetFile().getAsFile().get());
			if (getEqualsHandler().isPresent()) {
				com.setEqualsHandler(this.getEqualsHandler().get());
			}
		}
	}
}