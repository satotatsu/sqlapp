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

package com.sqlapp.gradle.plugins.properties;

import javax.sql.DataSource;

import org.gradle.api.Project;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.command.AbstractTableCommand;
import com.sqlapp.data.db.command.properties.ConsoleOutputLevelProperty;
import com.sqlapp.data.db.command.properties.ContextProperty;
import com.sqlapp.data.db.command.properties.ConvertersProperty;
import com.sqlapp.data.db.command.properties.CsvEncodingProperty;
import com.sqlapp.data.db.command.properties.DataSourceProperty;
import com.sqlapp.data.db.command.properties.DirectoryProperty;
import com.sqlapp.data.db.command.properties.EncodingProperty;
import com.sqlapp.data.db.command.properties.EqualsHandlerProperty;
import com.sqlapp.data.db.command.properties.FileDirectoryProperty;
import com.sqlapp.data.db.command.properties.FileFilterProperty;
import com.sqlapp.data.db.command.properties.FilesProperty;
import com.sqlapp.data.db.command.properties.JsonConverterProperty;
import com.sqlapp.data.db.command.properties.ObjectTargetProperty;
import com.sqlapp.data.db.command.properties.OnlyCurrentCatalogProperty;
import com.sqlapp.data.db.command.properties.OnlyCurrentSchemaProperty;
import com.sqlapp.data.db.command.properties.OriginalFileProperty;
import com.sqlapp.data.db.command.properties.OutputDirectoryProperty;
import com.sqlapp.data.db.command.properties.OutputFileTypeProperty;
import com.sqlapp.data.db.command.properties.OutputFormatTypeProperty;
import com.sqlapp.data.db.command.properties.PlaceholderProperty;
import com.sqlapp.data.db.command.properties.QueryCommitIntervalProperty;
import com.sqlapp.data.db.command.properties.SchemaOptionProperty;
import com.sqlapp.data.db.command.properties.SchemaTargetProperty;
import com.sqlapp.data.db.command.properties.SheetNameProperty;
import com.sqlapp.data.db.command.properties.SqlExecutorProperty;
import com.sqlapp.data.db.command.properties.SqlProperty;
import com.sqlapp.data.db.command.properties.SqlTypeProperty;
import com.sqlapp.data.db.command.properties.TableOptionProperty;
import com.sqlapp.data.db.command.properties.TableTargetProperty;
import com.sqlapp.data.db.command.properties.TargetFileProperty;
import com.sqlapp.data.db.command.properties.UseSchemaNameDirectoryProperty;
import com.sqlapp.data.db.command.properties.UseTableNameDirectoryProperty;
import com.sqlapp.data.db.command.properties.YamlConverterProperty;
import com.sqlapp.gradle.plugins.extension.DataSourceExtension;
import com.sqlapp.gradle.plugins.extension.OptionsExtension;
import com.sqlapp.gradle.plugins.extension.TableOptionsExtension;
import com.sqlapp.jdbc.SqlappDataSource;
import com.sqlapp.util.JsonConverter;
import com.sqlapp.util.YamlConverter;

public enum TaskPropertiesEnum {
	CONSOLE_OUTPUT_LEVEL() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof ConsoleOutputLevelTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof ConsoleOutputLevelProperty)) {
				return;
			}
			final ConsoleOutputLevelTaskProperty extension = cast(taskProps);
			final ConsoleOutputLevelProperty prop = cast(obj);
			if (extension.getConsoleOutputLevel().isPresent()) {
				prop.setConsoleOutputLevel(extension.getConsoleOutputLevel().get());
			}
		}
	},
	CONTEXT() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof ContextTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof ContextProperty)) {
				return;
			}
			final ContextTaskProperty extension = cast(taskProps);
			final ContextProperty prop = cast(obj);
			if (extension.getParameters().isPresent()) {
				prop.getContext().clear();
				prop.getContext().putAll(extension.getParameters().get());
			}
		}
	},
	CONVERTERS() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof ConvertersTaskProperty;
		}

		@Override
		public void initialize(Project project, Object obj) {
			if (!isInstanceof(obj)) {
				return;
			}
			final ConvertersTaskProperty prop = cast(obj);
			prop.getConverters().convention(new Converters());
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof ConvertersProperty)) {
				return;
			}
			final ConvertersTaskProperty extension = cast(taskProps);
			final ConvertersProperty prop = cast(obj);
			if (extension.getConverters().isPresent()) {
				prop.setConverters(extension.getConverters().get());
			}
		}
	},
	CSV_ENCODING() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof CsvEncodingTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof CsvEncodingProperty)) {
				return;
			}
			final CsvEncodingTaskProperty extension = cast(taskProps);
			final CsvEncodingProperty prop = cast(obj);
			if (extension.getCsvEncoding().isPresent()) {
				prop.setCsvEncoding(extension.getCsvEncoding().get());
			}
		}
	},
	DATA_SOURCE() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof DataSourceTaskProperty;
		}

		@Override
		public void initialize(Project project, Object obj) {
			if (!isInstanceof(obj)) {
				return;
			}
			final DataSourceTaskProperty prop = cast(obj);
			prop.setDataSource(project.getObjects().newInstance((DataSourceExtension.class)));
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof DataSourceProperty)) {
				return;
			}
			final DataSourceTaskProperty extension = cast(taskProps);
			final DataSourceProperty prop = cast(obj);
			final DataSource ds = extension.getDataSource().createDataSource();
			if (DEBUG.isInstanceof(taskProps)) {
				final DebugTaskProperty debugProperty = cast(taskProps);
				if (debugProperty.getDebug().getOrElse(false)) {
					final SqlappDataSource sds = new SqlappDataSource(ds);
					sds.setDebug(true);
					prop.setDataSource(sds);
				} else {
					prop.setDataSource(ds);
				}
			} else {
				prop.setDataSource(ds);
			}
		}
	},
	DEBUG() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof DebugTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof ContextProperty)) {
				return;
			}
			final DebugTaskProperty extension = cast(taskProps);
			if (extension.getDebug().getOrElse(false)) {
				if (extension instanceof ContextProperty) {
					ContextTaskProperty contextProperty = cast(taskProps);
					System.out.println("parameters=" + contextProperty.getParameters().get());
				}
			}
		}
	},
	DIRECTORY() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof DirectoryTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof DirectoryProperty)) {
				return;
			}
			final DirectoryTaskProperty extension = cast(taskProps);
			final DirectoryProperty prop = cast(obj);
			if (extension.getDirectory().isPresent()) {
				prop.setDirectory(extension.getDirectory().get().getAsFile());
			}
		}
	},
	ENCODING() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof EncodingTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof EncodingProperty)) {
				return;
			}
			final EncodingTaskProperty extension = cast(taskProps);
			final EncodingProperty prop = cast(obj);
			if (extension.getEncoding().isPresent()) {
				prop.setEncoding(extension.getEncoding().get());
			}
		}
	},
	EQUALS_HANDLER() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof EqualsHandlerTaskProperty;
		}

		@Override
		public void initialize(Project project, Object obj) {
			if (!isInstanceof(obj)) {
				return;
			}
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof EqualsHandlerProperty)) {
				return;
			}
			final EqualsHandlerTaskProperty extension = cast(taskProps);
			final EqualsHandlerProperty prop = cast(obj);
			if (extension.getEqualsHandler().isPresent()) {
				prop.setEqualsHandler(extension.getEqualsHandler().get());
			}
		}
	},
	FILE_DIRECTORY() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof FileDirectoryTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof FileDirectoryProperty)) {
				return;
			}
			final FileDirectoryTaskProperty extension = cast(taskProps);
			final FileDirectoryProperty prop = cast(obj);
			if (extension.getFileDirectory().isPresent()) {
				prop.setFileDirectory(extension.getFileDirectory().get().getAsFile());
			}
		}
	},
	FILE_FILTER() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof FileFilterTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof FileFilterProperty)) {
				return;
			}
			final FileFilterTaskProperty extension = cast(taskProps);
			final FileFilterProperty prop = cast(obj);
			prop.setFileFilter(extension.getFileFilter());
		}
	},
	FILES() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof FilesTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof FilesProperty)) {
				return;
			}
			final FilesTaskProperty extension = cast(taskProps);
			final FilesProperty prop = cast(obj);
			if (!extension.getFiles().isEmpty()) {
				prop.setFiles(extension.getFiles().getFiles());
			}
		}
	},
	GENERATE_SQL() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof GenerateSqlTaskProperties;
		}

		@Override
		public void initialize(Project project, Object obj) {
			if (!isInstanceof(obj)) {
				return;
			}
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof GenerateSqlTaskProperties)) {
				return;
			}
		}
	},
	JSON_CONVERTER() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof JsonConverterTaskProperty;
		}

		@Override
		public void initialize(Project project, Object obj) {
			if (!isInstanceof(obj)) {
				return;
			}
			final JsonConverterTaskProperty prop = cast(obj);
			final JsonConverter jsonConverter = new JsonConverter();
			jsonConverter.setIndentOutput(true);
			prop.getJsonConverter().convention(jsonConverter);
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof JsonConverterProperty)) {
				return;
			}
			final JsonConverterTaskProperty extension = cast(taskProps);
			final JsonConverterProperty prop = cast(obj);
			if (extension.getJsonConverter().isPresent()) {
				prop.setJsonConverter(extension.getJsonConverter().get());
			}
		}
	},
	OBJECT_TARGET() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof ObjectTargetTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof ObjectTargetProperty)) {
				return;
			}
			final ObjectTargetTaskProperty extension = cast(taskProps);
			final ObjectTargetProperty prop = cast(obj);
			if (extension.getIncludeObjects().isPresent()) {
				prop.setIncludeObjects(extension.getIncludeObjects().get().toArray(new String[0]));
			}
			if (extension.getExcludeObjects().isPresent()) {
				prop.setExcludeObjects(extension.getExcludeObjects().get().toArray(new String[0]));
			}
		}
	},
	ONLY_CURRENT_CATALOG() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof OnlyCurrentCatalogTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof OnlyCurrentCatalogProperty)) {
				return;
			}
			final OnlyCurrentCatalogTaskProperty extension = cast(taskProps);
			final OnlyCurrentCatalogProperty prop = cast(obj);
			if (extension.getOnlyCurrentCatalog().isPresent()) {
				prop.setOnlyCurrentCatalog(extension.getOnlyCurrentCatalog().get());
			}
		}
	},
	ONLY_CURRENT_SCHEMA() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof OnlyCurrentSchemaTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof OnlyCurrentSchemaTaskProperty)) {
				return;
			}
			final OnlyCurrentSchemaTaskProperty extension = cast(taskProps);
			final OnlyCurrentSchemaProperty prop = cast(obj);
			if (extension.getOnlyCurrentSchema().isPresent()) {
				prop.setOnlyCurrentSchema(extension.getOnlyCurrentSchema().get());
			}
		}
	},
	ORIGINAL_FILE() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof OriginalFileTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof OriginalFileProperty)) {
				return;
			}
			final OriginalFileTaskProperty extension = cast(taskProps);
			final OriginalFileProperty prop = cast(obj);
			if (extension.getOriginalFile().isPresent()) {
				prop.setOriginalFile(extension.getOriginalFile().get().getAsFile());
			}
		}
	},
	OUTPUT_DIRECTORY() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof OutputDirectoryTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof OutputDirectoryProperty)) {
				return;
			}
			final OutputDirectoryTaskProperty extension = cast(taskProps);
			final OutputDirectoryProperty prop = cast(obj);
			if (extension.getOutputDirectory().isPresent()) {
				prop.setOutputDirectory(extension.getOutputDirectory().get().getAsFile());
			}
		}
	},
	OUTPUT_FILE_TYPE() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof OutputFileTypeTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof OutputFileTypeProperty)) {
				return;
			}
			final OutputFileTypeTaskProperty extension = cast(taskProps);
			final OutputFileTypeProperty prop = cast(obj);
			if (extension.getOutputFileType().isPresent()) {
				prop.setOutputFileType(extension.getOutputFileType().get());
			}
		}
	},
	OUTPUT_FORMAT_TYPE() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof OutputFormatTypeTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof OutputFormatTypeProperty)) {
				return;
			}
			final OutputFormatTypeTaskProperty extension = cast(taskProps);
			final OutputFormatTypeProperty prop = cast(obj);
			if (extension.getOutputFormatType().isPresent()) {
				prop.setOutputFormatType(extension.getOutputFormatType().get());
			}
		}
	},
	PLACEHOLDER() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof PlaceholderTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof PlaceholderProperty)) {
				return;
			}
			final PlaceholderTaskProperty extension = cast(taskProps);
			final PlaceholderProperty prop = cast(obj);
			if (extension.getPlaceholderPrefix().isPresent()) {
				prop.setPlaceholderPrefix(extension.getPlaceholderPrefix().get());
			}
			if (extension.getPlaceholderSuffix().isPresent()) {
				prop.setPlaceholderSuffix(extension.getPlaceholderSuffix().get());
			}
			if (extension.getPlaceholders().isPresent()) {
				prop.setPlaceholders(extension.getPlaceholders().get());
			}
		}
	},
	QUERY_COMMIT_INTERVAL() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof QueryCommitIntervalTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof QueryCommitIntervalProperty)) {
				return;
			}
			final QueryCommitIntervalTaskProperty extension = cast(taskProps);
			final QueryCommitIntervalProperty prop = cast(obj);
			if (extension.getQueryCommitInterval().isPresent()) {
				prop.setQueryCommitInterval(extension.getQueryCommitInterval().get());
			}
		}
	},
	SCHEMA_OPTION() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof SchemaOptionTaskProperty;
		}

		@Override
		public void initialize(Project project, Object obj) {
			if (!isInstanceof(obj)) {
				return;
			}
			final SchemaOptionTaskProperty prop = cast(obj);
			prop.getSchemaOptions().convention(project.getObjects().newInstance(OptionsExtension.class));
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof SchemaOptionProperty)) {
				return;
			}
			final SchemaOptionTaskProperty extension = cast(taskProps);
			final SchemaOptionProperty com = cast(obj);
			if (extension.getSchemaOptions().isPresent()) {
				com.setSchemaOptions(extension.getSchemaOptions().get());
			}
		}
	},
	SCHEMA_TARGET() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof SchemaTargetTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof SchemaTargetProperty)) {
				return;
			}
			final SchemaTargetTaskProperty extension = cast(taskProps);
			final SchemaTargetProperty prop = cast(obj);
			if (extension.getIncludeSchemas().isPresent()) {
				prop.setIncludeSchemas(extension.getIncludeSchemas().get().toArray(new String[0]));
			}
			if (extension.getExcludeSchemas().isPresent()) {
				prop.setExcludeSchemas(extension.getExcludeSchemas().get().toArray(new String[0]));
			}
		}
	},
	SHEET_NAME() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof SheetNameTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof SheetNameProperty)) {
				return;
			}
			final SheetNameTaskProperty extension = cast(taskProps);
			final SheetNameProperty prop = cast(obj);
			if (extension.getSheetName().isPresent()) {
				prop.setSheetName(extension.getSheetName().get());
			}
		}
	},
	SQL() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof SqlTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof SqlProperty)) {
				return;
			}
			final SqlTaskProperty extension = cast(taskProps);
			final SqlProperty prop = cast(obj);
			if (extension.getSql().isPresent()) {
				prop.setSql(extension.getSql().get());
			}
		}
	},
	SQL_TYPE() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof SqlTypeTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof SqlTypeProperty)) {
				return;
			}
			final SqlTypeTaskProperty extension = cast(taskProps);
			final SqlTypeProperty prop = cast(obj);
			if (extension.getSqlType().isPresent()) {
				prop.setSqlType(extension.getSqlType().get());
			}
		}
	},
	SQL_EXECUTOR() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof SqlExecutorTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof SqlExecutorProperty)) {
				return;
			}
			final SqlExecutorTaskProperty extension = cast(taskProps);
			final SqlExecutorProperty prop = cast(obj);
			if (extension.getSqlExecutor().isPresent()) {
				prop.setSqlExecutor(extension.getSqlExecutor().get());
			}
		}
	},
	TABLE_OPTION() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof TableOptionTaskProperty;
		}

		@Override
		public void initialize(Project project, Object obj) {
			if (!isInstanceof(obj)) {
				return;
			}
			final TableOptionTaskProperty prop = cast(obj);
			prop.getTableOptions().convention(project.getObjects().newInstance(TableOptionsExtension.class));
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof TableOptionProperty)) {
				return;
			}
			final TableOptionTaskProperty extension = cast(taskProps);
			final TableOptionProperty prop = cast(obj);
			if (extension.getTableOptions().isPresent()) {
				prop.setTableOptions(extension.getTableOptions().get());
			}
		}
	},
	TARGET_FILE() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof TargetFileTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof TargetFileProperty)) {
				return;
			}
			final TargetFileTaskProperty extension = cast(taskProps);
			final TargetFileProperty prop = cast(obj);
			if (extension.getTargetFile().isPresent()) {
				prop.setTargetFile(extension.getTargetFile().get().getAsFile());
			}
		}
	},
	TABLE_TARGET() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof TableTargetTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof AbstractTableCommand)) {
				return;
			}
			final TableTargetTaskProperty extension = cast(taskProps);
			final TableTargetProperty prop = cast(obj);
			if (extension.getIncludeTables().isPresent()) {
				prop.setIncludeTables(extension.getIncludeTables().get().toArray(new String[0]));
			}
			if (extension.getExcludeTables().isPresent()) {
				prop.setExcludeTables(extension.getExcludeTables().get().toArray(new String[0]));
			}
		}
	},
	USE_SCHEMA_NAME_DIRECTORY() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof UseSchemaNameDirectoryTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof UseSchemaNameDirectoryProperty)) {
				return;
			}
			final UseSchemaNameDirectoryTaskProperty extension = cast(taskProps);
			final UseSchemaNameDirectoryProperty prop = cast(obj);
			if (extension.getUseSchemaNameDirectory().isPresent()) {
				prop.setUseSchemaNameDirectory(extension.getUseSchemaNameDirectory().get());
			}
		}
	},
	USE_TABLE_NAME_DIRECTORY() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof UseTableNameDirectoryTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof UseTableNameDirectoryProperty)) {
				return;
			}
			final UseTableNameDirectoryTaskProperty extension = cast(taskProps);
			final UseTableNameDirectoryProperty prop = cast(obj);
			if (extension.getUseTableNameDirectory().isPresent()) {
				prop.setUseTableNameDirectory(extension.getUseTableNameDirectory().get());
			}
		}
	},
	YAML_CONVERTER() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof YamlConverterTaskProperty;
		}

		@Override
		public void initialize(Project project, Object obj) {
			if (!isInstanceof(obj)) {
				return;
			}
			final YamlConverterTaskProperty prop = cast(obj);
			final YamlConverter jsonConverter = new YamlConverter();
			jsonConverter.setIndentOutput(true);
			prop.getYamlConverter().convention(jsonConverter);
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof YamlConverterProperty)) {
				return;
			}
			final YamlConverterTaskProperty extension = cast(taskProps);
			final YamlConverterProperty prop = cast(obj);
			if (extension.getYamlConverter().isPresent()) {
				prop.setYamlConverter(extension.getYamlConverter().get());
			}
		}
	},;

	public boolean isInstanceof(Object obj) {
		return false;
	}

	public void initialize(Project project, Object obj) {

	}

	public void setProperty(Object taskProps, Object obj) {

	}

	@SuppressWarnings({ "unchecked" })
	private static <T> T cast(Object obj) {
		return (T) obj;
	}

	public static void initializeAll(Project project, Object target) {
		for (TaskPropertiesEnum enm : values()) {
			if (!enm.isInstanceof(target)) {
				continue;
			}
			enm.initialize(project, target);
		}
	}

	public static void setAllProperties(Object taskProps, Object obj) {
		for (TaskPropertiesEnum enm : values()) {
			enm.setProperty(taskProps, obj);
		}
	}

	public static void setDebugProperties(Object taskProps, Object obj) {
		TaskPropertiesEnum.DEBUG.setProperty(taskProps, obj);
		TaskPropertiesEnum.CONTEXT.setProperty(taskProps, obj);
		TaskPropertiesEnum.CONSOLE_OUTPUT_LEVEL.setProperty(taskProps, obj);
	}

}
