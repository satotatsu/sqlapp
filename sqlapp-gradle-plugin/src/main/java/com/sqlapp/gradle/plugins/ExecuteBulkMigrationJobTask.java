/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.Action;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.migration.BulkMigrationJobLeaseConfiguration;
import com.sqlapp.data.db.command.migration.ExecuteBulkMigrationJobCommand;
import com.sqlapp.jdbc.bulk.BulkMigrationJobListener;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlan;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationListener;

/** Executes a programmatically assembled bulk migration job. */
@DisableCachingByDefault(because = "Executes mutations against an external database")
public abstract class ExecuteBulkMigrationJobTask
		extends AbstractDbTask<ExecuteBulkMigrationJobCommand> {

	public void call(final Action<ExecuteBulkMigrationJobTask> action) {
		action.execute(this);
	}

	@Internal
	public abstract Property<BulkMigrationJobPlan> getPlan();

	@Internal
	public abstract Property<BulkMigrationJobListener> getListener();

	@Internal
	public abstract Property<ChunkedBulkMigrationListener> getChunkListener();

	@Internal
	public abstract Property<BulkMigrationJobLeaseConfiguration>
			getLeaseConfiguration();

	@Override
	protected void beforeRun(final ExecuteBulkMigrationJobCommand command) {
		command.setPlan(getPlan().get());
		if (getListener().isPresent()) {
			command.setListener(getListener().get());
		}
		if (getChunkListener().isPresent()) {
			command.setChunkListener(getChunkListener().get());
		}
		if (getLeaseConfiguration().isPresent()) {
			command.setLeaseConfiguration(getLeaseConfiguration().get());
		}
	}

	@Override
	protected ExecuteBulkMigrationJobCommand createCommand() {
		return new ExecuteBulkMigrationJobCommand();
	}
}
