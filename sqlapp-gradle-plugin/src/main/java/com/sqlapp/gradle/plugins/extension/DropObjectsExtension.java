package com.sqlapp.gradle.plugins.extension;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.DropObjectsCommand;

/**
 * DropObject用のExtension
 */
public abstract class DropObjectsExtension extends AbstractDbTableExtension {
	@Inject
	public DropObjectsExtension(Project project) {
		super(project);
	}

	@Internal
	public void call(Action<DropObjectsExtension> cons) {
		cons.execute(this);
	}

	/**
	 * 現在のカタログのみを対象とするフラグ
	 */
	@Input
	@Optional
	public abstract Property<Boolean> getOnlyCurrentCatalog();

	/**
	 * Include drop target Objects
	 */
	@Input
	@Optional
	public abstract ListProperty<String> getIncludeObjects();

	/**
	 * Exclude drop target Objects
	 */
	@Input
	@Optional
	public abstract ListProperty<String> getExcludeObjects();

	/**
	 * オブジェクトのDROPを実施
	 */
	@Input
	@Optional
	public abstract Property<Boolean> getDropObjects();

	/**
	 * テーブルのDROPを実施
	 */
	@Input
	@Optional
	public abstract Property<Boolean> getDropTables();

	@Input
	@Optional
	public abstract Property<String> getDreDropTableSql();

	@Input
	@Optional
	public abstract Property<String> getAfterDropTableSql();

	@Internal
	@Override
	public void setCommand(AbstractCommand command, boolean debug) {
		super.setCommand(command, debug);
		if (command instanceof DropObjectsCommand) {
			DropObjectsCommand com = (DropObjectsCommand) command;
			if (getOnlyCurrentCatalog().isPresent()) {
				com.setOnlyCurrentCatalog(getOnlyCurrentCatalog().get());
			}
			if (getIncludeObjects().isPresent()) {
				com.setIncludeObjects(getIncludeObjects().get().toArray(new String[0]));
			}
			if (getExcludeObjects().isPresent()) {
				com.setExcludeObjects(getExcludeObjects().get().toArray(new String[0]));
			}
		}
	}
}
