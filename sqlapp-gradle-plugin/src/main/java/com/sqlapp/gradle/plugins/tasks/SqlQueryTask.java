package com.sqlapp.gradle.plugins.tasks;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import com.sqlapp.data.db.command.OutputFormatType;
import com.sqlapp.data.db.command.SqlQueryCommand;
import com.sqlapp.gradle.plugins.extension.DataSourceInject;
import com.sqlapp.util.FileUtils;

public abstract class SqlQueryTask extends AbstractDbTask implements DataSourceInject {
	@Input
	@Optional
	public abstract Property<String> getSql();

	/**
	 * Output targetFile
	 */
	@InputFile
	@Optional
	public abstract RegularFileProperty getSqlFile();

	/** encoding */
	@Input
	@Optional
	public abstract Property<String> getEncoding();

	/** encoding */
	@Input
	@Optional
	public abstract Property<String> getOutputFormatType();

	@Input
	@Optional
	OutputFormatType outputFormatType = null;

	@TaskAction
	public void exec() {
		SqlQueryCommand command = new SqlQueryCommand();
		command.setDataSource(createDataSource(this.getDataSource()));
		if (getSql().isPresent()) {
			command.setSql(getSql().get());
		}
		if (!getSqlFile().isPresent()) {
			command.setSql(FileUtils.readText(getSqlFile().get().getAsFile(),
					getEncoding().isPresent() ? getEncoding().get() : "UTF-8"));
		}
		if (getOutputFormatType().isPresent()) {
			command.setOutputFormatType(OutputFormatType.parse(getOutputFormatType().get()));
		}
		run(command);
	}

}