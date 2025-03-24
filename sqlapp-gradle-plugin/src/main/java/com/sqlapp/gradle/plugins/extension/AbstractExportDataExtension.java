package com.sqlapp.gradle.plugins.extension;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.export.AbstractExportCommand;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.util.JsonConverter;

/**
 * Table用のExtension
 */

public abstract class AbstractExportDataExtension extends AbstractDbTableExtension {
	@Inject
	protected AbstractExportDataExtension(Project project) {
		super(project);
	}

	/**
	 * Output Directory
	 */
	@InputDirectory
	public abstract DirectoryProperty getDirectory(); // = new File("./");

	@Input
	@Optional
	public abstract Property<Boolean> getUseSchemaNameDirectory(); // = false;

	@Input
	@Optional
	public abstract Property<String> getCsvEncoding();// = Charset.defaultCharset().toString();

	public abstract Property<JsonConverter> getJsonConverter();

	@Nested
	@Optional
	public abstract TableOptions getTableOptions();

	public void tableOptions(Action<? super TableOptions> action) {
		action.execute(getTableOptions());
	}

	@Internal
	@Override
	public void setCommand(AbstractCommand command, boolean debug) {
		super.setCommand(command, debug);
		if (command instanceof AbstractExportCommand) {
			AbstractExportCommand com = (AbstractExportCommand) command;
			if (getUseSchemaNameDirectory().isPresent()) {
				com.setUseSchemaNameDirectory(getUseSchemaNameDirectory().get());
			}
			if (getCsvEncoding().isPresent()) {
				com.setCsvEncoding(getCsvEncoding().get());
			}
			if (getJsonConverter().isPresent()) {
				com.setJsonConverter(getJsonConverter().get());
			}
			if (getDirectory().isPresent()) {
				com.setDirectory(getDirectory().getAsFile().get());
			}
			if (getTableOptions() != null) {
				com.setTableOptions(getTableOptions());
			}
		}
	}
}
