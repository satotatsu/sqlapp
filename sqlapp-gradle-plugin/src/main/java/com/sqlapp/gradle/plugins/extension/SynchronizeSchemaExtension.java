package com.sqlapp.gradle.plugins.extension;

import java.io.File;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.SynchronizeSchemaCommand;
import com.sqlapp.data.db.sql.SqlExecutor;
import com.sqlapp.data.schemas.EqualsHandler;

public abstract class SynchronizeSchemaExtension extends AbstractSchemaFileExtension {
	@Inject
	public SynchronizeSchemaExtension(Project project) {
		super(project);
	}

	@Internal
	public void call(Action<SynchronizeSchemaExtension> cons) {
		cons.execute(this);
	}

	@Input
	@Optional
	public abstract Property<EqualsHandler> getEqualsHandler();

	public void equalsHandler(Action<? super Property<EqualsHandler>> action) {
		action.execute(getEqualsHandler());
	}

	@InputFile
	@Optional
	public abstract ConfigurableFileCollection getFiles();

	@Input
	@Optional
	public abstract Property<SqlExecutor> getSqlExecutor();

	public void sqlExecutor(Action<? super SqlExecutor> action) {
		if (getSqlExecutor().isPresent()) {
			action.execute(getSqlExecutor().get());
		}
	}

	@Internal
	@Override
	public void setCommand(AbstractCommand command, boolean debug) {
		super.setCommand(command, debug);
		if (command instanceof SynchronizeSchemaCommand) {
			SynchronizeSchemaCommand com = (SynchronizeSchemaCommand) command;
			if (getEqualsHandler().isPresent()) {
				com.setEqualsHandler(getEqualsHandler().get());
			}
			if (getFiles().isEmpty()) {
				com.setFiles(getFiles().getFiles().toArray(new File[0]));
			}
			if (getSqlExecutor().isPresent()) {
				com.setSqlExecutor(getSqlExecutor().get());
			}
		}
	}
}
