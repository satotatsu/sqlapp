package com.sqlapp.gradle.plugins.extension;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.html.AbstractSchemaFileCommand;

/**
 * GenerateSql用のExtension
 */
public abstract class AbstractGenerateSqlExtension extends AbstractDbExtension {
	@Inject
	protected AbstractGenerateSqlExtension(Project project) {
		super(project);
	}

	/**
	 * Output targetFile
	 */
	@InputFile
	@Optional
	public abstract RegularFileProperty getTargetFile();

	/**
	 * 出力ファイルパス
	 */
	@InputDirectory
	@Optional
	public abstract DirectoryProperty getOutputPath();

	/**
	 * 出力ファイルエンコーディング
	 */
	@Input
	@Optional
	public abstract Property<String> getEncoding();

	/**
	 * 複数ファイル出力
	 */
	@Input
	@Optional
	public abstract Property<Boolean> getOutputAsMultiFiles();

	@Input
	@Optional
	public abstract Property<String> getOutputFileExtension();

	@Input
	@Optional
	public abstract Property<Long> getLastChangeNumber();

	@Input
	@Optional
	public abstract Property<Long> getChangeNumberStep();

	@Input
	@Optional
	public abstract Property<Integer> getNumberOfDigits();

	@Internal
	public int getOrElseNumberOfDigits() {
		return getNumberOfDigits().getOrElse(19);
	}

	@Input
	@Optional
	public abstract Property<OptionsExtension> getSchemaOptions();

	public void schemaOptions(Action<? super OptionsExtension> action) {
		action.execute(getSchemaOptions().get());
	}

	@Internal
	@Override
	public void setCommand(AbstractCommand command, boolean debug) {
		super.setCommand(command, debug);
		if (command instanceof AbstractSchemaFileCommand) {
			AbstractSchemaFileCommand com = (AbstractSchemaFileCommand) command;
			if (getTargetFile().isPresent()) {
				com.setTargetFile(getTargetFile().getAsFile().get());
			}
		}
	}
}
