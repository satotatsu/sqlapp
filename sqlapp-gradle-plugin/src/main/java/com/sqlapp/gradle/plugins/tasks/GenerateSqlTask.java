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

import org.gradle.api.tasks.TaskAction;

import com.sqlapp.data.db.command.GenerateSimpleSqlCommand;
import com.sqlapp.data.db.sql.FileSqlExecutor;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.StandardOutSqlExecutor;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.gradle.plugins.extension.GenerateSqlExtension;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;

public abstract class GenerateSqlTask extends AbstractGenerateSqlTask {

	@TaskAction
	public void exec() {
		final GenerateSimpleSqlCommand command = new GenerateSimpleSqlCommand();
		final GenerateSqlExtension obj = this.getProject().getExtensions().getByType(GenerateSqlExtension.class);
		obj.setCommand(command, getDebug().getOrElse(false));
		try {
			DbCommonObject<?> xmlObj = SchemaUtils.readXml(obj.getTargetFile().getAsFile().get());
			command.setTarget(xmlObj);
		} catch (Exception e) {
			throw new RuntimeException();
		}
		if (obj.getSchemaOptions().isPresent()) {
			command.setSchemaOption(obj.getSchemaOptions().get());
		}
		File outputPath = obj.getOutputPath().get().getAsFile();
		run(command);
		if (outputPath == null) {
			StandardOutSqlExecutor executor = new StandardOutSqlExecutor();
			execute(executor, command.getOperations());
		} else {
			if (this.getDebug().getOrElse(false)) {
				StandardOutSqlExecutor executor = new StandardOutSqlExecutor();
				execute(executor, command.getOperations());
			}
			long step = obj.getChangeNumberStep().getOrElse(10L);
			String encoding = obj.getEncoding().getOrElse("UTF-8");
			if (obj.getOutputAsMultiFiles().getOrElse(true)) {
				if (!outputPath.exists()) {
					outputPath.mkdirs();
				}
				long current = getCurrentNumber(obj);
				String suffix = getFileSuffix(obj);
				for (SqlOperation operation : command.getOperations()) {
					current = current + step;
					String fname = "" + getFilename(current, obj.getOrElseNumberOfDigits(),
							toString(operation.getSqlType()) + "_" + getName(operation), suffix);
					File file = new File(outputPath, fname);
					FileSqlExecutor executor = new FileSqlExecutor(file, encoding);
					execute(executor, operation);
				}
			} else {
				FileUtils.createParentDirectory(outputPath);
				SqlOperation operation = CommonUtils.first(command.getOperations());
				if (outputPath.exists()) {
					if (outputPath.isDirectory()) {
						long current = getCurrentNumber(obj);
						current = current + step;
						String suffix = getFileSuffix(obj);
						String fname = "" + getFilename(current, obj.getOrElseNumberOfDigits(),
								toString(operation.getSqlType()) + "_" + getName(operation), suffix);
						File file = new File(outputPath, fname);
						FileSqlExecutor executor = new FileSqlExecutor(file, encoding);
						execute(executor, command.getOperations());
						return;
					}
				}
				FileSqlExecutor executor = new FileSqlExecutor(outputPath, encoding);
				execute(executor, command.getOperations());
			}
		}
	}
}
