package com.sqlapp.gradle.plugins.extension;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.html.AbstractSchemaFileCommand;
import com.sqlapp.util.JsonConverter;

/**
 * Schema用のExtension
 */
public abstract class AbstractSchemaFileExtension extends AbstractDbExtension {
	@Inject
	protected AbstractSchemaFileExtension(Project project) {
		super(project);
		targetFile = project.getObjects().fileProperty();
	}

	private final RegularFileProperty targetFile;

	@InputFile
	public RegularFileProperty getTargetFile() {
		return targetFile;
	}

	@Input
	@Optional
	public abstract DirectoryProperty getDictionaryFileDirectory();

	@Input
	@Optional
	public abstract Property<String> getDictionaryFileType();

	@Input
	@Optional
	public abstract Property<String> getCsvEncoding();

	@Input
	@Optional
	public abstract Property<JsonConverter> getJsonConverter();

	public void jsonConverter(Action<? super JsonConverter> action) {
		if (getJsonConverter().isPresent()) {
			action.execute(getJsonConverter().get());
		}
	}

	@Internal
	@Override
	public void setCommand(AbstractCommand command, boolean debug) {
		super.setCommand(command, debug);
		if (command instanceof AbstractSchemaFileCommand) {
			AbstractSchemaFileCommand com = (AbstractSchemaFileCommand) command;
			com.setTargetFile(getTargetFile().getAsFile().get());
			if (getDictionaryFileDirectory().isPresent()) {
				com.setDictionaryFileDirectory(getDictionaryFileDirectory().getAsFile().get());
			}
			if (getDictionaryFileType().isPresent()) {
				com.setDictionaryFileType(getDictionaryFileType().get());
			}
			if (getCsvEncoding().isPresent()) {
				com.setCsvEncoding(getCsvEncoding().get());
			}
			if (getJsonConverter().isPresent()) {
				com.setJsonConverter(getJsonConverter().get());
			}
		}
	}
}
