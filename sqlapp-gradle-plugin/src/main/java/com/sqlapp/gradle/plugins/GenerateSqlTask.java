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
import java.util.List;
import java.util.stream.Collectors;

import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.GenerateSimpleSqlCommand;
import com.sqlapp.data.db.sql.FileSqlExecutor;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.StandardOutSqlExecutor;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaNameFilter;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.TableNameFilter;
import com.sqlapp.gradle.plugins.properties.SchemaTargetTaskProperty;
import com.sqlapp.gradle.plugins.properties.SqlTypeTaskProperty;
import com.sqlapp.gradle.plugins.properties.TableTargetTaskProperty;
import com.sqlapp.util.CommonUtils;

@DisableCachingByDefault
public abstract class GenerateSqlTask extends AbstractGenerateSqlTask<GenerateSimpleSqlCommand>
		implements SqlTypeTaskProperty, SchemaTargetTaskProperty, TableTargetTaskProperty {

	@Override
	protected GenerateSimpleSqlCommand createCommand() {
		return new GenerateSimpleSqlCommand();
	}

	@Override
	protected void run(GenerateSimpleSqlCommand command) {
		try {
			DbCommonObject<?> xmlObj = SchemaUtils.readXml(getTargetFile().getAsFile().get());
			command.setTarget(xmlObj);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		File outputDirectory = null;
		if (getOutputDirectory().isPresent()) {
			outputDirectory = getOutputDirectory().get().getAsFile();
		}
		super.run(command);
		List<SqlOperation> sqlOperations = filterOperations(command.getSqlOperations());
		if (outputDirectory == null) {
			final StandardOutSqlExecutor executor = new StandardOutSqlExecutor();
			execute(executor, sqlOperations);
		} else {
			if (this.getDebug().getOrElse(false)) {
				final StandardOutSqlExecutor executor = new StandardOutSqlExecutor();
				execute(executor, sqlOperations);
			}
			long step = getOrElseChangeNumberStep();
			String encoding = getEncoding().getOrElse("UTF-8");
			if (getOutputAsMultiFiles().getOrElse(true)) {
				if (!outputDirectory.exists()) {
					outputDirectory.mkdirs();
				}
				long current = getCurrentNumber();
				String suffix = getFileSuffix();
				for (final SqlOperation operation : sqlOperations) {
					current = current + step;
					String fname = "" + getFilename(current, getOrElseNumberOfDigits(),
							toString(operation.getSqlType()) + "_" + getName(operation), suffix);
					final File file = new File(outputDirectory, fname);
					final FileSqlExecutor executor = new FileSqlExecutor(file, encoding);
					execute(executor, operation);
				}
			} else {
				if (!outputDirectory.exists()) {
					outputDirectory.mkdirs();
				}
				SqlOperation operation = CommonUtils.first(sqlOperations);
				long current = getCurrentNumber();
				current = current + step;
				final String suffix = getFileSuffix();
				final String fname = "" + getFilename(current, getOrElseNumberOfDigits(),
						toString(operation.getSqlType()) + "_" + getName(operation), suffix);
				final File file = new File(outputDirectory, fname);
				final FileSqlExecutor executor = new FileSqlExecutor(file, encoding);
				execute(executor, sqlOperations);
			}
		}
	}

	private List<SqlOperation> filterOperations(List<SqlOperation> operations) {
		SchemaNameFilter schemaNameFilter = new SchemaNameFilter();
		if (getIncludeSchemas().isPresent() && !getIncludeSchemas().get().isEmpty()) {
			schemaNameFilter.setInclude(getIncludeSchemas().get().toArray(new String[0]));
		}
		if (getExcludeSchemas().isPresent() && !getExcludeSchemas().get().isEmpty()) {
			schemaNameFilter.setExclude(getExcludeSchemas().get().toArray(new String[0]));
		}
		TableNameFilter tableNameFilter = new TableNameFilter();
		if (getIncludeTables().isPresent() && !getIncludeTables().get().isEmpty()) {
			tableNameFilter.setInclude(getIncludeTables().get().toArray(new String[0]));
		}
		if (getExcludeTables().isPresent() && !getExcludeTables().get().isEmpty()) {
			tableNameFilter.setExclude(getExcludeTables().get().toArray(new String[0]));
		}
		return operations.stream().filter(o -> {
			if (o.getOriginal() != null) {
				if ((o.getOriginal() instanceof Schema) || (o.getOriginal() instanceof AbstractSchemaObject)) {
					if (!schemaNameFilter.test(o.getOriginal())) {
						return false;
					}
					if (!tableNameFilter.test(o.getOriginal())) {
						return false;
					}
				}
				return true;
			}
			return true;
		}).collect(Collectors.toList());
	}
}
