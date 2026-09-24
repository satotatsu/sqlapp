/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-gradle-plugin.
 *
 * sqlapp-gradle-plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-gradle-plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-gradle-plugin.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.gradle.plugins.extension;

import java.time.Duration;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.migration.MigrationCommand;
import com.sqlapp.gradle.plugins.properties.EncodingTaskProperty;
import com.sqlapp.gradle.plugins.properties.FileDirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.PlaceholderTaskProperty;
import com.sqlapp.gradle.plugins.properties.RecursiveTaskProperty;

public abstract class MigrationExtension extends AbstractDbExtension
		implements FileDirectoryTaskProperty, PlaceholderTaskProperty, EncodingTaskProperty, RecursiveTaskProperty {
	@Inject
	public MigrationExtension(ObjectFactory objects) {
		super(objects);
		this.setDataSource(objects.newInstance((DataSourceExtension.class)));
		getChecksumValidation().convention(false);
		getRejectOutOfOrder().convention(false);
		getRejectNonTransactional().convention(false);
		getRequireDownMigration().convention(false);
	}

	public void call(Action<MigrationExtension> cons) {
		cons.execute(this);
	}

	/**
	 * バージョンアップ用SQLのディレクトリ
	 */
	@InputDirectory
	@Optional
	public abstract DirectoryProperty getSqlDirectory();

	/**
	 * バージョンダウン用のSQLのディレクトリ
	 */
	@InputDirectory
	@Optional
	public abstract DirectoryProperty getDownSqlDirectory();

	/**
	 * バージョンアップ前に実行するSQLのディレクトリ
	 */
	@InputDirectory
	@Optional
	public abstract DirectoryProperty getSetupSqlDirectory();

	/**
	 * バージョンアップ後に実行するSQLのディレクトリ
	 */
	@InputDirectory
	@Optional
	public abstract DirectoryProperty getFinalizeSqlDirectory();

	@Input
	@Optional
	public abstract Property<String> getLastChangeNumber();

	@Input
	@Optional
	public abstract Property<Boolean> getShowVersionOnly();

	/** Optional checksum recording and validation; disabled by default. */
	@Input
	public abstract Property<Boolean> getChecksumValidation();

	/** Optional strict rejection of migrations inserted below the current version. */
	@Input
	public abstract Property<Boolean> getRejectOutOfOrder();

	/** Optional rejection of migrations selected for non-transactional execution. */
	@Input
	public abstract Property<Boolean> getRejectNonTransactional();

	/** Optional requirement that each selected up migration has down SQL. */
	@Input
	public abstract Property<Boolean> getRequireDownMigration();

	/** Optional expected live Schema XML immediately before migration. */
	@InputFile
	@Optional
	public abstract RegularFileProperty getPreMigrationSchemaFile();

	/** Optional reviewed migration-plan JSON required to match execution. */
	@InputFile
	@Optional
	public abstract RegularFileProperty getExpectedPlanFile();

	/** Optional maximum age, in seconds, of the reviewed migration plan. */
	@Input
	@Optional
	public abstract Property<Long> getExpectedPlanMaxAgeSeconds();

	/** Optional externally approved SHA-256 fingerprint of the reviewed plan. */
	@Input
	@Optional
	public abstract Property<String> getExpectedPlanFingerprint();

	/** Optional JDBC timeout for acquiring the migration-history lock. */
	@Input
	@Optional
	public abstract Property<Integer> getLockTimeoutSeconds();

	@Input
	@Optional
	public abstract Property<Boolean> getWithSeriesNumber();

	/** Schema Change log table name */
	@Nested
	public abstract ChangeTableExtension getChangeTable();

	public void changeTable(Action<? super ChangeTableExtension> action) {
		action.execute(getChangeTable());
	}

	@Internal
	public void initializeCommand(AbstractCommand command) {
		super.initializeCommand(command);
		if (command instanceof MigrationCommand) {
			MigrationCommand com = (MigrationCommand) command;
			com.setChecksumValidation(getChecksumValidation().get());
			com.setRejectOutOfOrder(getRejectOutOfOrder().get());
			com.setRejectNonTransactional(getRejectNonTransactional().get());
			com.setRequireDownMigration(getRequireDownMigration().get());
			if (getPreMigrationSchemaFile().isPresent()) {
				com.setPreMigrationSchemaFile(getPreMigrationSchemaFile().get().getAsFile());
			}
			if (getExpectedPlanFile().isPresent()) {
				com.setExpectedPlanFile(getExpectedPlanFile().get().getAsFile());
			}
			if (getExpectedPlanMaxAgeSeconds().isPresent()) {
				com.setExpectedPlanMaxAge(Duration.ofSeconds(getExpectedPlanMaxAgeSeconds().get()));
			}
			if (getExpectedPlanFingerprint().isPresent()) {
				com.setExpectedPlanFingerprint(getExpectedPlanFingerprint().get());
			}
			if (getLockTimeoutSeconds().isPresent()) {
				com.setLockTimeoutSeconds(getLockTimeoutSeconds().get());
			}
			if (getSqlDirectory().isPresent()) {
				com.setSqlDirectory(getSqlDirectory().get().getAsFile());
			}
			if (getDownSqlDirectory().isPresent()) {
				com.setDownSqlDirectory(getDownSqlDirectory().get().getAsFile());
			}
			if (getSetupSqlDirectory().isPresent()) {
				com.setSetupSqlDirectory(getSetupSqlDirectory().get().getAsFile());
			}
			if (getFinalizeSqlDirectory().isPresent()) {
				com.setFinalizeSqlDirectory(getFinalizeSqlDirectory().get().getAsFile());
			}
			if (getLastChangeNumber().isPresent()) {
				com.setLastChangeToApply(Long.valueOf(getLastChangeNumber().get()));
			}
			if (getShowVersionOnly().isPresent()) {
				com.setShowVersionOnly(getShowVersionOnly().get());
			}
			if (getWithSeriesNumber().isPresent()) {
				com.setWithSeriesNumber(getWithSeriesNumber().get());
			}
			getChangeTable().initializeCommand(command);
		}
	}
}
