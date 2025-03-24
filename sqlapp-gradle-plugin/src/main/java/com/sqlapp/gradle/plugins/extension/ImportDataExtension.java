package com.sqlapp.gradle.plugins.extension;

import java.io.File;
import java.util.function.Predicate;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.export.ImportDataFromFileCommand;
import com.sqlapp.data.db.sql.SqlType;

/**
 * ImportData用のExtension
 */
public abstract class ImportDataExtension extends AbstractExportDataExtension {
	@Inject
	public ImportDataExtension(Project project) {
		super(project);
	}

	@Internal
	public void call(Action<ImportDataExtension> cons) {
		cons.execute(this);
	}

	@Input
	@Optional
	public abstract Property<Boolean> getUseTableNameDirectory();

	@Input
	@Optional
	public abstract Property<Long> getQueryCommitInterval();

	/** file directory */
	@InputDirectory
	@Optional
	public abstract DirectoryProperty getFileDirectory();

	/** SQL Type */
	@Input
	@Optional
	public abstract Property<String> getSqlType();

	@Input
	@Optional
	public abstract Property<Predicate<File>> getFileFilter();

	@Input
	@Optional
	public abstract Property<String> getPlaceholderPrefix();

	@Input
	@Optional
	public abstract Property<String> getPlaceholderSuffix();

	@Input
	@Optional
	public abstract Property<Boolean> getPlaceholders();

	@Internal
	@Override
	public void setCommand(AbstractCommand command, boolean debug) {
		super.setCommand(command, debug);
		if (command instanceof ImportDataFromFileCommand) {
			ImportDataFromFileCommand com = (ImportDataFromFileCommand) command;
			if (getUseTableNameDirectory().isPresent()) {
				com.setUseTableNameDirectory(getUseTableNameDirectory().get());
			}
			if (getQueryCommitInterval().isPresent()) {
				com.setQueryCommitInterval(getQueryCommitInterval().get());
			}
			if (getFileDirectory().isPresent()) {
				com.setFileDirectory(getFileDirectory().get().getAsFile());
			}
			if (getSqlType().isPresent()) {
				com.setSqlType(SqlType.parse(getSqlType().get()));
			}
			if (getFileFilter().isPresent()) {
				com.setFileFilter(getFileFilter().get());
			}
			//
			if (getPlaceholderPrefix().isPresent()) {
				com.setPlaceholderPrefix(getPlaceholderPrefix().get());
			}
			if (getPlaceholderSuffix().isPresent()) {
				com.setPlaceholderSuffix(getPlaceholderSuffix().get());
			}
			if (getPlaceholders().isPresent()) {
				com.setPlaceholders(getPlaceholders().get());
			}
		}
	}
}
