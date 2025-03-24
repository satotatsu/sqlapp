package com.sqlapp.gradle.plugins.extension;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.tasks.Internal;

import com.sqlapp.data.db.command.AbstractCommand;

public abstract class AbstractExtension {

	private final Project project;

	@Inject
	protected AbstractExtension(Project project) {
		this.project = project;
	}

	@Internal
	public Project getProject() {
		return project;
	}

	@Internal
	public void setCommand(AbstractCommand command, boolean debug) {

	}

}
