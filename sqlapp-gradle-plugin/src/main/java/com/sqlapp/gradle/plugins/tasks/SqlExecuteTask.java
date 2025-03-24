package com.sqlapp.gradle.plugins.tasks;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import com.sqlapp.data.db.command.SqlExecuteCommand;
import com.sqlapp.gradle.plugins.extension.DataSourceInject;
import com.sqlapp.gradle.plugins.extension.PlaceholderInject;

public abstract class SqlExecuteTask extends AbstractDbTask implements DataSourceInject, PlaceholderInject {
	@Input
	@Optional
	public abstract Property<String> getSqlText();

	@InputFile
	@Optional
	public abstract ConfigurableFileCollection getSqlFiles();

	/** encoding */
	@Input
	@Optional
	public abstract Property<String> getEncoding();

	@Input
	@Optional
	public abstract Property<String> getPlaceholderPrefix();

	@Input
	@Optional
	public abstract Property<String> getPlaceholderSuffix();

	@Input
	@Optional
	public abstract Property<Boolean> getPlaceholders();

	@TaskAction
	public void exec() {
		final SqlExecuteCommand command = new SqlExecuteCommand();
		command.setDataSource(createDataSource(this.getDataSource()));
		if (getSqlText().isPresent()) {
			command.setSqlText(getSqlText().get());
		}
		if (!getSqlFiles().isEmpty()) {
			command.setSqlFiles(getSqlFiles().getFiles());
		}
		if (getEncoding().isPresent()) {
			command.setEncoding(getEncoding().get());
		}
		this.setPlaceholders(command);
		run(command);
	}

}