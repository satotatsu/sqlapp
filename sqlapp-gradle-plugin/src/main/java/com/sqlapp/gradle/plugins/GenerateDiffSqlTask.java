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

package com.sqlapp.gradle.plugins;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.GenerateDiffSqlCommand;
import com.sqlapp.data.db.sql.FileSqlExecutor;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.StandardOutSqlExecutor;
import com.sqlapp.data.schemas.DefaultSchemaEqualsHandler;
import com.sqlapp.data.schemas.EqualsHandler;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.gradle.plugins.properties.EqualsHandlerTaskProperty;
import com.sqlapp.gradle.plugins.properties.OriginalFileTaskProperty;
import com.sqlapp.util.FileUtils;

@DisableCachingByDefault
public abstract class GenerateDiffSqlTask extends AbstractGenerateSqlTask<GenerateDiffSqlCommand>
		implements EqualsHandlerTaskProperty, OriginalFileTaskProperty {

	@Override
	protected GenerateDiffSqlCommand createCommand() {
		return new GenerateDiffSqlCommand();
	}

	@Input
	public abstract Property<Boolean> getWithVersionDown();

	private EqualsHandler equalsHandler = new DefaultSchemaEqualsHandler();

	@Internal
	public EqualsHandler getEqualsHandler() {
		return equalsHandler;
	}

	public void setEqualsHandler(EqualsHandler equalsHandler) {
		this.equalsHandler = equalsHandler;
	}

	@Override
	protected void beforeRun(GenerateDiffSqlCommand command) {
		try {
			command.setOriginal(SchemaUtils.readXml(getOriginalFile().get().getAsFile()));
			command.setTarget(SchemaUtils.readXml(getTargetFile().get().getAsFile()));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected void run(GenerateDiffSqlCommand command) {
		File outputDirectory = null;
		if (getOutputDirectory().isPresent()) {
			outputDirectory = getOutputDirectory().get().getAsFile();
		}
		String encoding = getEncoding().getOrElse("UTF-8");
		super.run(command);
		if (command.getSqlOperations().isEmpty()) {
			return;
		}
		if (outputDirectory == null) {
			final StandardOutSqlExecutor executor = new StandardOutSqlExecutor();
			execute(executor, command.getSqlOperations());
		} else {
			if (this.getDebug().getOrElse(false)) {
				final StandardOutSqlExecutor executor = new StandardOutSqlExecutor();
				execute(executor, command.getSqlOperations().toArray(new SqlOperation[0]));
			}
			long step = getOrElseChangeNumberStep();
			if (getOutputAsMultiFiles().getOrElse(true)) {
				if (!outputDirectory.exists()) {
					outputDirectory.mkdirs();
				}
				long current = createCurrentNumber();
				final String suffix = createFileSuffix();
				for (SqlOperation operation : command.getSqlOperations()) {
					current = current + step;
					final File file = new File(outputDirectory, "" + createFilename(current, getOrElseNumberOfDigits(),
							toString(operation.getSqlType()) + "_" + createName(operation), suffix));
					final FileSqlExecutor executor = new FileSqlExecutor(file, encoding);
					execute(executor, operation);
				}
			} else {
				FileUtils.createParentDirectory(outputDirectory);
				final List<SqlOperation> sqlOperations = command.getSqlOperations();
				if (sqlOperations.isEmpty()) {
					return;
				}
				List<SqlOperation> reverseSqlOperations = null;
				if (getWithVersionDown().getOrElse(false)) {
					command.swap();
					super.run(command);
					reverseSqlOperations = command.getSqlOperations();
					reverseSqlOperations.add(0, SqlOperation.EMPTY_LINE_OPERATION);
					sqlOperations.addAll(reverseSqlOperations);
				}
				final SqlOperation operation = getSqlOperation(sqlOperations);
				if (outputDirectory.exists()) {
					if (outputDirectory.isDirectory()) {
						long current = createCurrentNumber();
						current = current + step;
						String suffix = createFileSuffix();
						File file = new File(outputDirectory, "" + createFilename(current, getOrElseNumberOfDigits(),
								toString(operation.getSqlType()) + "_" + createName(operation), suffix));
						FileSqlExecutor executor = new FileSqlExecutor(file, encoding);
						execute(executor, sqlOperations);
						return;
					}
				}
				final FileSqlExecutor executor = new FileSqlExecutor(outputDirectory, encoding);
				execute(executor, sqlOperations);
			}
		}
	}

	private SqlOperation getSqlOperation(final List<SqlOperation> sqlOperations) {
		for (SqlOperation sqlOperation : sqlOperations) {
			if (sqlOperation.getSqlType() != SqlType.SET_SEARCH_PATH_TO_SCHEMA) {
				return sqlOperation;
			}
		}
		return sqlOperations.get(0);
	}
}
