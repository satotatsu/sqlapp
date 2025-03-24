package com.sqlapp.gradle.plugins.extension;

import java.io.IOException;

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
import com.sqlapp.data.db.command.GenerateDiffSqlCommand;
import com.sqlapp.data.schemas.EqualsHandler;
import com.sqlapp.data.schemas.SchemaUtils;

/**
 * Schema用のExtension
 */
public abstract class GenerateDiffSqlExtension extends AbstractGenerateSqlExtension {
	@Inject
	public GenerateDiffSqlExtension(Project project) {
		super(project);
	}

	@Internal
	public void call(Action<GenerateDiffSqlExtension> cons) {
		cons.execute(this);
	}

	/**
	 * Output originalFilePath
	 */
	@InputFile
	public abstract RegularFileProperty getOriginalFile();

	@Input
	public abstract Property<Boolean> getWithVersionDown();

	@Input
	@Optional
	public abstract Property<EqualsHandler> getEqualsHandler();

	@Internal
	@Override
	public void setCommand(AbstractCommand command, boolean debug) {
		super.setCommand(command, debug);
		if (command instanceof GenerateDiffSqlCommand) {
			GenerateDiffSqlCommand com = (GenerateDiffSqlCommand) command;
			try {
				com.setOriginal(SchemaUtils.readXml(getOriginalFile().get().getAsFile()));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			try {
				com.setTarget(SchemaUtils.readXml(getTargetFile().get().getAsFile()));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			if (getEqualsHandler().isPresent()) {
				com.setEqualsHandler(getEqualsHandler().get());
			}
		}
	}
}
