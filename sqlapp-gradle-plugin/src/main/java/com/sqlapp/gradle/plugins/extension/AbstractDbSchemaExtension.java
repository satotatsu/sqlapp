package com.sqlapp.gradle.plugins.extension;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.AbstractTableCommand;

/**
 * Schema用のExtension
 */
public abstract class AbstractDbSchemaExtension extends AbstractDbExtension {

	@Inject
	protected AbstractDbSchemaExtension(Project project) {
		super(project);
	}

	/**
	 * 現在のカタログのみを対象とするフラグ
	 */
	@Input
	@Optional
	public abstract Property<Boolean> getOnlyCurrentCatalog();

	/**
	 * 現在のスキーマのみを対象とするフラグ
	 */
	@Input
	@Optional
	public abstract Property<Boolean> getOnlyCurrentSchema();

	/**
	 * ダンプに含めるスキーマ
	 */
	@Input
	@Optional
	public abstract ListProperty<String> getIncludeSchemas();

	/**
	 * ダンプから除くスキーマ
	 */
	@Input
	@Optional
	public abstract ListProperty<String> getExcludeSchemas();

	/**
	 * ダンプから除くスキーマ
	 */
	@Input
	@Optional
	@Nested
	public abstract OptionsExtension getOptions();

	public void options(Action<? super OptionsExtension> action) {
		action.execute(getOptions());
	}

	@Internal
	@Override
	public void setCommand(AbstractCommand command, boolean debug) {
		super.setCommand(command, debug);
		if (command instanceof AbstractTableCommand) {
			AbstractTableCommand com = (AbstractTableCommand) command;
			if (getOnlyCurrentCatalog().isPresent()) {
				com.setOnlyCurrentCatalog(getOnlyCurrentCatalog().get());
			}
			if (getOnlyCurrentSchema().isPresent()) {
				com.setOnlyCurrentSchema(getOnlyCurrentSchema().get());
			}
			if (getIncludeSchemas().isPresent()) {
				com.setIncludeSchemas(getIncludeSchemas().get().toArray(new String[0]));
			}
			if (getExcludeSchemas().isPresent()) {
				com.setExcludeSchemas(getExcludeSchemas().get().toArray(new String[0]));
			}
		}
	}
}
