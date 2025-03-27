/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.gradle.plugins.tasks;

import java.io.File;
import java.util.List;

import org.gradle.api.tasks.TaskAction;

import com.sqlapp.data.db.command.GenerateDiffSqlCommand;
import com.sqlapp.data.db.sql.FileSqlExecutor;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.StandardOutSqlExecutor;
import com.sqlapp.gradle.plugins.extension.GenerateDiffSqlExtension;
import com.sqlapp.util.FileUtils;

public abstract class GenerateDiffSqlTask extends AbstractGenerateSqlTask {

	@TaskAction
	public void exec() {
		final GenerateDiffSqlCommand command = new GenerateDiffSqlCommand();
		final GenerateDiffSqlExtension obj = this.getProject().getExtensions()
				.getByType(GenerateDiffSqlExtension.class);
		obj.setCommand(command, getDebug().getOrElse(false));
		File outputPath = obj.getOutputPath().get().getAsFile();
		if (obj.getOutputPath().isPresent()) {
			outputPath = obj.getOutputPath().get().getAsFile();
		} else {
			outputPath = new File("./");
		}
		String encoding = obj.getEncoding().getOrElse("UTF-8");
		if (obj.getEqualsHandler().isPresent()) {
			command.setEqualsHandler(obj.getEqualsHandler().get());
		}
		run(command);
		if (command.getSqlOperations().isEmpty()) {
			return;
		}
		if (outputPath == null) {
			StandardOutSqlExecutor executor = new StandardOutSqlExecutor();
			execute(executor, command.getSqlOperations());
		} else {
			if (this.getDebug().getOrElse(false)) {
				StandardOutSqlExecutor executor = new StandardOutSqlExecutor();
				execute(executor, command.getSqlOperations().toArray(new SqlOperation[0]));
			}
			long step = obj.getChangeNumberStep().getOrElse(10L);
			if (obj.getOutputAsMultiFiles().getOrElse(true)) {
				if (!outputPath.exists()) {
					outputPath.mkdirs();
				}
				long current = getCurrentNumber(obj);
				String suffix = getFileSuffix(obj);
				for (SqlOperation operation : command.getSqlOperations()) {
					current = current + step;
					File file = new File(outputPath, "" + getFilename(current, obj.getOrElseNumberOfDigits(),
							toString(operation.getSqlType()) + "_" + getName(operation), suffix));
					FileSqlExecutor executor = new FileSqlExecutor(file, encoding);
					execute(executor, operation);
				}
			} else {
				FileUtils.createParentDirectory(outputPath);
				List<SqlOperation> sqlOperations = command.getSqlOperations();
				if (sqlOperations.isEmpty()) {
					return;
				}
				List<SqlOperation> reverseSqlOperations = null;
				if (obj.getWithVersionDown().getOrElse(false)) {
					command.swap();
					run(command);
					reverseSqlOperations = command.getSqlOperations();
					reverseSqlOperations.add(0, SqlOperation.EMPTY_LINE_OPERATION);
					reverseSqlOperations.add(0, SqlOperation.UNDO_OPERATION);
					reverseSqlOperations.add(0, SqlOperation.EMPTY_LINE_OPERATION);
					reverseSqlOperations.add(0, SqlOperation.COMMENT_SEPARATOR_OPERATION);
					sqlOperations.addAll(reverseSqlOperations);
				}
				SqlOperation operation = getSqlOperation(sqlOperations);
				if (outputPath.exists()) {
					if (outputPath.isDirectory()) {
						long current = getCurrentNumber(obj);
						current = current + step;
						String suffix = getFileSuffix(obj);
						File file = new File(outputPath, "" + getFilename(current, obj.getOrElseNumberOfDigits(),
								toString(operation.getSqlType()) + "_" + getName(operation), suffix));
						FileSqlExecutor executor = new FileSqlExecutor(file, encoding);
						execute(executor, sqlOperations);
						return;
					}
				}
				FileSqlExecutor executor = new FileSqlExecutor(outputPath, encoding);
				execute(executor, sqlOperations);
			}
		}
	}

	private SqlOperation getSqlOperation(List<SqlOperation> sqlOperations) {
		for (SqlOperation sqlOperation : sqlOperations) {
			if (sqlOperation.getSqlType() != SqlType.SET_SEARCH_PATH_TO_SCHEMA) {
				return sqlOperation;
			}
		}
		return sqlOperations.get(0);
	}
}
