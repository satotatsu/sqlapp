package com.sqlapp.gradle.plugins.extension;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.AbstractTableCommand;

/**
 * Table用のExtension
 */

public abstract class AbstractDbTableExtension extends AbstractDbSchemaExtension {
	@Inject
	protected AbstractDbTableExtension(Project project) {
		super(project);
	}

	/**
	 * ダンプに含めるテーブル
	 */
	@Input
	@Optional
	public abstract ListProperty<String> getIncludeTables();

	/**
	 * ダンプから除くテーブル
	 */
	@Input
	@Optional
	public abstract ListProperty<String> getExcludeTables();

	@Internal
	@Override
	public void setCommand(AbstractCommand command, boolean debug) {
		super.setCommand(command, debug);
		if (command instanceof AbstractTableCommand) {
			AbstractTableCommand com = (AbstractTableCommand) command;
			if (getIncludeTables().isPresent()) {
				com.setIncludeTables(getIncludeTables().get().toArray(new String[0]));
			}
			if (getExcludeTables().isPresent()) {
				com.setExcludeTables(getExcludeTables().get().toArray(new String[0]));
			}
		}
	}
}
