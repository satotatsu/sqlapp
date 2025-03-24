package com.sqlapp.gradle.plugins.extension;

import java.util.function.Predicate;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.html.UpdateDictionariesCommand;

public abstract class UpdateDictionariesExtension extends AbstractSchemaFileExtension {
	@Inject
	public UpdateDictionariesExtension(Project project) {
		super(project);
	}

	@Internal
	public void call(Action<UpdateDictionariesExtension> cons) {
		cons.execute(this);
	}

	@Input
	@Optional
	public abstract Property<Predicate<String>> getWithSchema();

	@Input
	@Optional
	public abstract Property<Boolean> getOutputRemarksAsDisplayName();

	@Internal
	@Override
	public void setCommand(AbstractCommand command, boolean debug) {
		super.setCommand(command, debug);
		if (command instanceof UpdateDictionariesCommand) {
			UpdateDictionariesCommand com = (UpdateDictionariesCommand) command;
			if (getWithSchema().isPresent()) {
				com.setWithSchema(getWithSchema().get());
			}
			if (getOutputRemarksAsDisplayName().isPresent()) {
				com.setOutputRemarksAsDisplayName(getOutputRemarksAsDisplayName().get());
			}
		}
	}
}
