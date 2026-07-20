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

import java.util.function.Consumer;

import javax.sql.DataSource;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;

import com.sqlapp.data.db.command.generator.factory.TableGeneratorConfigFactory;
import com.sqlapp.data.db.command.properties.CommitPerSqlTypeProperty;
import com.sqlapp.data.db.command.properties.CommitPerTableProperty;
import com.sqlapp.data.db.command.properties.ConsoleOutputLevelProperty;
import com.sqlapp.data.db.command.properties.ContextProperty;
import com.sqlapp.data.db.command.properties.ConvertersProperty;
import com.sqlapp.data.db.command.properties.CsvEncodingProperty;
import com.sqlapp.data.db.command.properties.DataSourceProperty;
import com.sqlapp.data.db.command.properties.DictionaryFileDirectoryProperty;
import com.sqlapp.data.db.command.properties.DictionaryFileTypeProperty;
import com.sqlapp.data.db.command.properties.DirectoryProperty;
import com.sqlapp.data.db.command.properties.DmlBatchSizeProperty;
import com.sqlapp.data.db.command.properties.EncodingProperty;
import com.sqlapp.data.db.command.properties.EqualsHandlerProperty;
import com.sqlapp.data.db.command.properties.FetchSizeProperty;
import com.sqlapp.data.db.command.properties.FileDirectoryProperty;
import com.sqlapp.data.db.command.properties.FileTypeProperty;
import com.sqlapp.data.db.command.properties.ForeignKeyDefinitionDirectoryProperty;
import com.sqlapp.data.db.command.properties.GeneratorConfigFactoryProperty;
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
import com.sqlapp.data.db.command.properties.RecursiveProperty;
import com.sqlapp.data.db.command.properties.RemoveOriginalFileProperty;
import com.sqlapp.data.db.command.properties.SchemaOptionsProperty;
import com.sqlapp.data.db.command.properties.SchemaTargetProperty;
import com.sqlapp.data.db.command.properties.SheetNameProperty;
import com.sqlapp.data.db.command.properties.SqlExecutorProperty;
import com.sqlapp.data.db.command.properties.SqlProperty;
import com.sqlapp.data.db.command.properties.SqlTypeProperty;
import com.sqlapp.data.db.command.properties.SqlTypesProperty;
import com.sqlapp.data.db.command.properties.TableOptionsProperty;
import com.sqlapp.data.db.command.properties.TableTargetProperty;
import com.sqlapp.data.db.command.properties.TargetFileProperty;
import com.sqlapp.data.db.command.properties.TomlConverterProperty;
import com.sqlapp.data.db.command.properties.UseSchemaNameDirectoryProperty;
import com.sqlapp.data.db.command.properties.YamlConverterProperty;
import com.sqlapp.data.db.sql.Options;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.gradle.plugins.extension.DataSourceExtension;
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
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof ConvertersProperty)) {
				return;
			}
			final ConvertersTaskProperty extension = cast(taskProps);
			final ConvertersProperty prop = cast(obj);
			if (extension.getConverters() != null) {
				prop.setConverters(extension.getConverters().getConverters());
			}
		}
	},
	COMMIT_PER_SQL_TYPE() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof CommitPerSqlTypeTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof CommitPerSqlTypeProperty)) {
				return;
			}
			final CommitPerSqlTypeTaskProperty extension = cast(taskProps);
			final CommitPerSqlTypeProperty prop = cast(obj);
			if (extension.getCommitPerSqlType().isPresent()) {
				prop.setCommitPerSqlType(extension.getCommitPerSqlType().get());
			}
		}
	},
	COMMIT_PER_TABLE() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof CommitPerTableTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof CommitPerTableProperty)) {
				return;
			}
			final CommitPerTableTaskProperty extension = cast(taskProps);
			final CommitPerTableProperty prop = cast(obj);
			if (extension.getCommitPerTable().isPresent()) {
				prop.setCommitPerTable(extension.getCommitPerTable().get());
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
		public void initialize(ObjectFactory objects, Object obj) {
			if (!isInstanceof(obj)) {
				return;
			}
			final DataSourceTaskProperty prop = cast(obj);
			prop.setDataSource(objects.newInstance((DataSourceExtension.class)));
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
					sds.setDebug(false);
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
				ContextTaskProperty contextProperty = cast(taskProps);
				System.out.println("parameters=" + contextProperty.getParameters().get());
			}
		}
	},
	DICTIONARY_FILE_DIRECTORY() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof DictionaryFileDirectoryTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof DictionaryFileDirectoryProperty)) {
				return;
			}
			final DictionaryFileDirectoryTaskProperty extension = cast(taskProps);
			final DictionaryFileDirectoryProperty prop = cast(obj);
			if (extension.getDictionaryFileDirectory().isPresent()) {
				prop.setDictionaryFileDirectory(extension.getDictionaryFileDirectory().get().getAsFile());
			}
		}
	},
	DICTIONARY_FILE_TYPE() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof DictionaryFileTypeTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof DictionaryFileTypeProperty)) {
				return;
			}
			final DictionaryFileTypeTaskProperty extension = cast(taskProps);
			final DictionaryFileTypeProperty prop = cast(obj);
			if (extension.getDictionaryFileType().isPresent()) {
				prop.setDictionaryFileType(extension.getDictionaryFileType().get());
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
	DML_BATCH_SIZE() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof DmlBatchSizeTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof DmlBatchSizeProperty)) {
				return;
			}
			final DmlBatchSizeTaskProperty extension = cast(taskProps);
			final DmlBatchSizeProperty prop = cast(obj);
			if (extension.getDmlBatchSize().isPresent()) {
				prop.setDmlBatchSize(extension.getDmlBatchSize().get());
				setTableOptions(obj, tableOptions -> {
					tableOptions.setDmlBatchSize(extension.getDmlBatchSize().get());
				});
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
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof EqualsHandlerProperty)) {
				return;
			}
			final EqualsHandlerTaskProperty extension = cast(taskProps);
			final EqualsHandlerProperty prop = cast(obj);
			if (extension.getEqualsHandler() != null) {
				prop.setEqualsHandler(extension.getEqualsHandler());
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
	FILE_TYPE() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof FileTypeTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof FileTypeProperty)) {
				return;
			}
			final FileTypeTaskProperty extension = cast(taskProps);
			final FileTypeProperty prop = cast(obj);
			if (extension.getFileType().isPresent()) {
				prop.setFileType(extension.getFileType().get());
			}
		}
	},
	FETCH_SIZE() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof FetchSizeTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof FetchSizeProperty)) {
				return;
			}
			final FetchSizeTaskProperty extension = cast(taskProps);
			final FetchSizeProperty prop = cast(obj);
			if (extension.getFetchSize().isPresent()) {
				prop.setFetchSize(extension.getFetchSize().get());
			}
		}
	},
	FOREIGN_KEY_DEFINITION_DIRECTORY() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof ForeignKeyDefinitionDirectoryTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof ForeignKeyDefinitionDirectoryProperty)) {
				return;
			}
			final ForeignKeyDefinitionDirectoryTaskProperty extension = cast(taskProps);
			final ForeignKeyDefinitionDirectoryProperty prop = cast(obj);
			if (extension.getForeignKeyDefinitionDirectory().isPresent()) {
				prop.setForeignKeyDefinitionDirectory(extension.getForeignKeyDefinitionDirectory().get().getAsFile());
			}
		}
	},
	GENERATOR_CONFIG_FACTORY() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof GeneratorConfigFactoryTaskProperty;
		}

		@Override
		public void initialize(ObjectFactory objects, Object obj) {
			if (!isInstanceof(obj)) {
				return;
			}
			final GeneratorConfigFactoryTaskProperty prop = cast(obj);
			TableGeneratorConfigFactory target = new TableGeneratorConfigFactory();
			prop.setGeneratorConfigFactory(target);
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof GeneratorConfigFactoryProperty)) {
				return;
			}
			final GeneratorConfigFactoryTaskProperty extension = cast(taskProps);
			final GeneratorConfigFactoryProperty prop = cast(obj);
			if (extension.getGeneratorConfigFactory() != null) {
				prop.setGeneratorConfigFactory(extension.getGeneratorConfigFactory());
			}
		}
	},
	GENERATE_SQL() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof GenerateSqlTaskProperties;
		}

		@Override
		public void initialize(ObjectFactory objects, Object obj) {
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
		public void initialize(ObjectFactory objects, Object obj) {
			if (!isInstanceof(obj)) {
				return;
			}
			final JsonConverterTaskProperty prop = cast(obj);
			JsonConverter conveter = new JsonConverter();
			conveter.setIndentOutput(true);
			prop.setJsonConverter(conveter);
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
			if (extension.getJsonConverter() != null) {
				prop.setJsonConverter(extension.getJsonConverter());
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
			if (isPresent(extension.getIncludeObjects())) {
				prop.setIncludeObjects(extension.getIncludeObjects().get().toArray(new String[0]));
			}
			if (isPresent(extension.getExcludeObjects())) {
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
			if (!(obj instanceof OnlyCurrentSchemaProperty)) {
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
	RECURSIVE() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof RecursiveTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof RecursiveProperty)) {
				return;
			}
			final RecursiveTaskProperty extension = cast(taskProps);
			final RecursiveProperty prop = cast(obj);
			if (extension.getRecursive().isPresent()) {
				prop.setRecursive(extension.getRecursive().getOrElse(false));
			}
		}
	},
	REMOVE_ORIGINAL_FILE() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof RemoveOriginalFileTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof RemoveOriginalFileProperty)) {
				return;
			}
			final RemoveOriginalFileTaskProperty extension = cast(taskProps);
			final RemoveOriginalFileProperty prop = cast(obj);
			if (extension.getRemoveOriginalFile().isPresent()) {
				prop.setRemoveOriginalFile(extension.getRemoveOriginalFile().getOrElse(false));
			}
		}
	},
	SCHEMA_OPTIONS() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof SchemaOptionTaskProperty;
		}

		@Override
		public void initialize(ObjectFactory objects, Object obj) {
			if (!isInstanceof(obj)) {
				return;
			}
			final SchemaOptionTaskProperty prop = cast(obj);
			prop.setSchemaOptions(new Options());
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof SchemaOptionsProperty)) {
				return;
			}
			final SchemaOptionTaskProperty extension = cast(taskProps);
			final SchemaOptionsProperty com = cast(obj);
			if (extension.getSchemaOptions() != null) {
				com.setSchemaOptions(extension.getSchemaOptions());
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
			if (isPresent(extension.getIncludeSchemas())) {
				prop.setIncludeSchemas(extension.getIncludeSchemas().get().toArray(new String[0]));
			}
			if (isPresent(extension.getExcludeSchemas())) {
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
	SQL_TYPES() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof SqlTypesTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {

			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof SqlTypesProperty)) {
				return;
			}
			final SqlTypesTaskProperty extension = cast(taskProps);
			final SqlTypesProperty prop = cast(obj);
			if (isPresent(extension.getSqlTypes())) {
				prop.setSqlTypes(extension.getSqlTypes().get().toArray(new String[0]));
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
	TABLE_OPTIONS() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof TableOptionsTaskProperty;
		}

		private static int DEFAULT_DML_BATCH_SIZE = 500;

		@Override
		public void initialize(ObjectFactory objects, Object obj) {
			if (!isInstanceof(obj)) {
				return;
			}
			final TableOptionsTaskProperty prop = cast(obj);
			TableOptions tableOptions = new TableOptions();
			tableOptions.setDmlBatchSize(DEFAULT_DML_BATCH_SIZE);
			prop.setTableOptions(tableOptions);
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof TableOptionsProperty)) {
				return;
			}
			final TableOptionsTaskProperty extension = cast(taskProps);
			final TableOptionsProperty prop = cast(obj);
			if (extension.getTableOptions() != null) {
				prop.setTableOptions(extension.getTableOptions());
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
			if (!(obj instanceof TableTargetProperty)) {
				return;
			}
			final TableTargetTaskProperty extension = cast(taskProps);
			final TableTargetProperty prop = cast(obj);
			if (isPresent(extension.getIncludeTables())) {
				prop.setIncludeTables(extension.getIncludeTables().get().toArray(new String[0]));
			}
			if (isPresent(extension.getExcludeTables())) {
				prop.setExcludeTables(extension.getExcludeTables().get().toArray(new String[0]));
			}
		}
	},
	TOML_CONVERTER() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof TomlConverterTaskProperty;
		}

		@Override
		public void setProperty(Object taskProps, Object obj) {
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!isInstanceof(taskProps)) {
				return;
			}
			if (!(obj instanceof TomlConverterProperty)) {
				return;
			}
			final TomlConverterTaskProperty extension = cast(taskProps);
			final TomlConverterProperty prop = cast(obj);
			if (extension.getTomlConverter() != null) {
				prop.setTomlConverter(extension.getTomlConverter());
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
	YAML_CONVERTER() {
		@Override
		public boolean isInstanceof(Object obj) {
			return obj instanceof YamlConverterTaskProperty;
		}

		@Override
		public void initialize(ObjectFactory objects, Object obj) {
			if (!isInstanceof(obj)) {
				return;
			}
			final YamlConverterTaskProperty prop = cast(obj);
			YamlConverter conveter = new YamlConverter();
			conveter.setIndentOutput(true);
			prop.setYamlConverter(conveter);
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
			if (extension.getYamlConverter() != null) {
				prop.setYamlConverter(extension.getYamlConverter());
			}
		}
	},;

	private static boolean isPresent(ListProperty<String> property) {
		if (!property.isPresent()) {
			return false;
		}
		return !property.get().isEmpty();
	}

	public boolean isInstanceof(Object obj) {
		return false;
	}

	public void initialize(ObjectFactory objects, Object obj) {

	}

	public void setProperty(Object taskProps, Object obj) {

	}

	public void setTableOptions(Object obj, Consumer<TableOptions> cons) {
		if (!TABLE_OPTIONS.isInstanceof(obj)) {
			return;
		}
		TableOptionsProperty prop = (TableOptionsProperty) obj;
		if (prop.getTableOptions() != null) {
			cons.accept(prop.getTableOptions());
		}
	}

	@SuppressWarnings({ "unchecked" })
	private static <T> T cast(Object obj) {
		return (T) obj;
	}

	public static void initializeAll(ObjectFactory objects, Object target) {
		for (TaskPropertiesEnum enm : values()) {
			if (!enm.isInstanceof(target)) {
				continue;
			}
			enm.initialize(objects, target);
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
