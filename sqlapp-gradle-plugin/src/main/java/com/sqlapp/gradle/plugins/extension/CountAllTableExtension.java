package com.sqlapp.gradle.plugins.extension;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.CountAllTablesCommand;
import com.sqlapp.data.db.command.OutputFormatType;

/**
 * Schema用のExtension
 */
public abstract class CountAllTableExtension extends AbstractDbTableExtension {
	@Inject
	public CountAllTableExtension(Project project) {
		super(project);
	}

	@Internal
	public void call(Action<CountAllTableExtension> cons) {
		cons.execute(this);
	}

	/**
	 * 出力フォーマット
	 */
	@Input
	@Optional
	public abstract Property<String> getOutputFormatType();

	@Internal
	@Override
	public void setCommand(AbstractCommand command, boolean debug) {
		super.setCommand(command, debug);
		if (command instanceof CountAllTablesCommand) {
			CountAllTablesCommand com = (CountAllTablesCommand) command;
			if (getOutputFormatType().isPresent()) {
				com.setOutputFormatType(OutputFormatType.parse(getOutputFormatType().get()));
			}
		}
	}
}
