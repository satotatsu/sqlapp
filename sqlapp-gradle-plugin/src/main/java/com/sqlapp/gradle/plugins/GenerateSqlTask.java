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

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
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
import com.sqlapp.gradle.plugins.extension.GenerateSqlExtension;
import com.sqlapp.util.CommonUtils;

@DisableCachingByDefault
public abstract class GenerateSqlTask extends AbstractGenerateSqlTask<GenerateSimpleSqlCommand, GenerateSqlExtension> {
	@Inject
	public GenerateSqlTask(ObjectFactory objectFactory) {
		super(objectFactory);
	}

	@Override
	protected GenerateSimpleSqlCommand createCommand() {
		return new GenerateSimpleSqlCommand();
	}

	@Override
	protected GenerateSqlExtension createExtension(Project project) {
		final GenerateSqlExtension obj = project.getExtensions().getByType(GenerateSqlExtension.class);
		return obj;
	}

	@Override
	protected void run(GenerateSimpleSqlCommand command) {
		GenerateSqlExtension obj = this.getExtension();
		try {
			DbCommonObject<?> xmlObj = SchemaUtils.readXml(obj.getTargetFile().getAsFile().get());
			command.setTarget(xmlObj);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		File outputDirectory = null;
		if (obj.getOutputDirectory().isPresent()) {
			outputDirectory = obj.getOutputDirectory().get().getAsFile();
		}
		super.run(command);
		List<SqlOperation> sqlOperations = filterOperations(obj, command.getSqlOperations());
		if (outputDirectory == null) {
			final StandardOutSqlExecutor executor = new StandardOutSqlExecutor();
			execute(executor, sqlOperations);
		} else {
			if (this.getDebug().getOrElse(false)) {
				final StandardOutSqlExecutor executor = new StandardOutSqlExecutor();
				execute(executor, sqlOperations);
			}
			long step = obj.getOrElseChangeNumberStep();
			String encoding = obj.getEncoding().getOrElse("UTF-8");
			if (obj.getOutputAsMultiFiles().getOrElse(true)) {
				if (!outputDirectory.exists()) {
					outputDirectory.mkdirs();
				}
				long current = getCurrentNumber(obj);
				String suffix = getFileSuffix(obj);
				for (final SqlOperation operation : sqlOperations) {
					current = current + step;
					String fname = "" + getFilename(current, obj.getOrElseNumberOfDigits(),
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
				long current = getCurrentNumber(obj);
				current = current + step;
				final String suffix = getFileSuffix(obj);
				final String fname = "" + getFilename(current, obj.getOrElseNumberOfDigits(),
						toString(operation.getSqlType()) + "_" + getName(operation), suffix);
				final File file = new File(outputDirectory, fname);
				final FileSqlExecutor executor = new FileSqlExecutor(file, encoding);
				execute(executor, sqlOperations);
			}
		}
	}

	private List<SqlOperation> filterOperations(GenerateSqlExtension extension, List<SqlOperation> operations) {
		SchemaNameFilter schemaNameFilter = new SchemaNameFilter();
		if (extension.getIncludeSchemas().isPresent() && !extension.getIncludeSchemas().get().isEmpty()) {
			schemaNameFilter.setInclude(extension.getIncludeSchemas().get().toArray(new String[0]));
		}
		if (extension.getExcludeSchemas().isPresent() && !extension.getExcludeSchemas().get().isEmpty()) {
			schemaNameFilter.setExclude(extension.getExcludeSchemas().get().toArray(new String[0]));
		}
		TableNameFilter tableNameFilter = new TableNameFilter();
		if (extension.getIncludeTables().isPresent() && !extension.getIncludeTables().get().isEmpty()) {
			tableNameFilter.setInclude(extension.getIncludeTables().get().toArray(new String[0]));
		}
		if (extension.getExcludeTables().isPresent() && !extension.getExcludeTables().get().isEmpty()) {
			tableNameFilter.setExclude(extension.getExcludeTables().get().toArray(new String[0]));
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
