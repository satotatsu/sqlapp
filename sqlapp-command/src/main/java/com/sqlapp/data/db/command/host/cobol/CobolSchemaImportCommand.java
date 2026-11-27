/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.host.cobol;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.host.cobol.CobolItem.Category;
import com.sqlapp.data.db.command.host.cobol.CobolItem.Usage;
import com.sqlapp.data.db.command.migration.legacy.LegacyMigrationMappingBuilder;
import com.sqlapp.data.db.command.migration.legacy.LegacyMigrationMappingIO;
import com.sqlapp.data.db.command.properties.OutputDirectoryProperty;
import com.sqlapp.data.db.command.properties.TargetFileProperty;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.YamlConverter;

import lombok.Getter;
import lombok.Setter;

/**
 * Imports COBOL copybook record descriptions into a relational schema XML
 * document and a legacy migration mapping that records the storage layout of
 * every column.
 * <p>
 * Each configured record becomes a table; group items are flattened into
 * columns, and OCCURS items become child tables keyed by the parent key and an
 * occurrence number. FILLER items are not imported. For REDEFINES, the
 * redefined item is imported unless the configuration selects one of the
 * redefining items.
 * </p>
 */
@Getter
@Setter
public class CobolSchemaImportCommand extends AbstractCommand implements TargetFileProperty, OutputDirectoryProperty {

	private File targetFile;

	private File configurationFile;

	private File outputDirectory = new File("./");

	private String outputFileName;

	private boolean migrationMappingEnabled = true;

	private File migrationMappingDirectory;

	private String migrationMappingFileName;

	private String encoding = "UTF-8";

	@Override
	protected void doRun() {
		validateProperties();
		execute(() -> {
			ImportConfiguration configuration = new YamlConverter().fromJsonString(configurationFile,
					ImportConfiguration.class);
			String source = Files.readString(targetFile.toPath(), Charset.forName(encoding));
			CobolCopybook copybook = new CobolCopybookParser().setSourceFormat(configuration.getSourceFormat())
					.setReplacing(configuration.getReplacing())
					.setDefaultRecordName(valueOr(configuration.getRecordName(),
							baseName(targetFile.getName()).toUpperCase(Locale.ROOT).replace('_', '-')))
					.parse(source);
			BuildResult result = new Builder(configuration).build(copybook);
			ensureDirectory(outputDirectory, "output");
			String xmlName = outputFileName;
			if (xmlName == null || xmlName.isBlank()) {
				xmlName = baseName(targetFile.getName()) + ".xml";
			}
			File outputFile = new File(outputDirectory, xmlName);
			result.schema.writeXml(outputFile);
			info("Output COBOL schema XML: " + outputFile.getAbsolutePath());
			for (String warning : result.warnings) {
				info("WARNING " + warning);
			}
			if (migrationMappingEnabled) {
				writeMigrationMapping(outputFile, result, configuration);
			}
		});
	}

	private void validateProperties() {
		if (targetFile == null || !targetFile.isFile()) {
			throw new CommandException("targetFile does not exist or is not a file: " + targetFile);
		}
		if (configurationFile == null || !configurationFile.isFile()) {
			throw new CommandException("configurationFile does not exist or is not a file: " + configurationFile);
		}
		if (outputDirectory == null) {
			throw new CommandException("outputDirectory is required.");
		}
	}

	private void writeMigrationMapping(File outputFile, BuildResult result, ImportConfiguration configuration) {
		File directory = migrationMappingDirectory != null ? migrationMappingDirectory : outputDirectory;
		ensureDirectory(directory, "migration mapping");
		String name = migrationMappingFileName;
		if (name == null || name.isBlank()) {
			name = baseName(targetFile.getName()) + "-legacy-migration.yaml";
		}
		File file = new File(directory, name);
		var mapping = new LegacyMigrationMappingBuilder().buildHostImportMapping(
				LegacyMigrationMappingBuilder.HostLanguage.COBOL, targetFile, outputFile, encoding,
				configuration.getSchemaName(), result.tableLogs, result.warnings);
		if (!result.controlRecords.isEmpty()) {
			// Control records (trailers) are not loaded; extractCobolCsv reads their layout
			// from here.
			mapping.getOptions().put("cobolControlRecords", result.controlRecords);
		}
		new LegacyMigrationMappingIO().write(file, mapping);
		info("Output legacy migration mapping: " + file.getAbsolutePath());
	}

	private static void ensureDirectory(File directory, String type) {
		if (!directory.exists() && !directory.mkdirs()) {
			throw new CommandException("Failed to create " + type + " directory: " + directory);
		}
	}

	private static String baseName(String name) {
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	private static String valueOr(String value, String defaultValue) {
		return value == null || value.isBlank() ? defaultValue : value;
	}

	/** COBOL data name to SQL identifier: hyphens become underscores. */
	static String sqlName(String cobolName) {
		return cobolName.replace('-', '_');
	}

	private record BuildResult(Schema schema, List<Map<String, Object>> tableLogs, List<String> warnings,
			Map<String, Object> controlRecords) {
	}

	/** Builds tables from parsed records; package-private for tests. */
	static final class Builder {
		private final ImportConfiguration configuration;
		private final List<String> warnings = new ArrayList<>();
		private final Set<String> usedColumnNames = new HashSet<>();
		private final Set<String> usedTableNames = new HashSet<>();
		private final Set<String> usedRedefines = new HashSet<>();
		private final Map<CobolItem, CobolItem> selectedAlternatives = new LinkedHashMap<>();
		private final Map<CobolItem, Column> columnByItem = new java.util.IdentityHashMap<>();

		Builder(ImportConfiguration configuration) {
			this.configuration = configuration;
		}

		private BuildResult build(CobolCopybook copybook) {
			if (configuration.getSchemaName() == null || configuration.getSchemaName().isBlank()) {
				throw new CommandException("schemaName is required.");
			}
			warnings.addAll(copybook.warnings());
			Schema schema = new Schema(configuration.getSchemaName());
			List<Map<String, Object>> tableLogs = new ArrayList<>();
			Set<String> includes = upper(configuration.getIncludeRecords());
			Set<String> excludes = upper(configuration.getExcludeRecords());
			for (CobolItem record : copybook.records()) {
				selectRedefines(record);
			}
			List<CobolItem> pending = new ArrayList<>();
			Map<String, Object> controlRecords = new LinkedHashMap<>();
			for (CobolItem record : copybook.records()) {
				String recordName = record.getName().toUpperCase(Locale.ROOT);
				if (!includes.isEmpty() && !includes.contains(recordName) || excludes.contains(recordName)) {
					continue;
				}
				RecordMapping recordMapping = configuration.mapping(record.getName());
				if (recordMapping == null) {
					warnings.add("No record mapping was provided: " + record.getName());
					continue;
				}
				if (recordMapping.isControl()) {
					if (recordMapping.getTableName() != null || !recordMapping.getPrimaryKey().isEmpty()
							|| recordMapping.getParent() != null || recordMapping.getSequenceColumn() != null) {
						throw new CommandException("A control record has no table; remove tableName, primaryKey,"
								+ " parent and sequenceColumn: record=" + record.getName());
					}
					controlRecords.put(record.getName(), controlLayout(record));
					continue;
				}
				pending.add(record);
			}
			// Parent records first, so that a detail record can inherit its header's key.
			Map<String, Table> recordTables = new LinkedHashMap<>();
			while (!pending.isEmpty()) {
				CobolItem next = pending.stream().filter(record -> {
					String parent = configuration.mapping(record.getName()).getParent();
					if (parent != null && controlRecords.keySet().stream().anyMatch(parent::equalsIgnoreCase)) {
						throw new CommandException(
								"A control record cannot be a parent: " + record.getName() + " -> " + parent);
					}
					return parent == null || recordTables.containsKey(parent.toUpperCase(Locale.ROOT));
				}).findFirst()
						.orElseThrow(
								() -> new CommandException(
										"Parent record is not imported or the parents form a cycle: " + pending.stream()
												.map(record -> record.getName() + " -> "
														+ configuration.mapping(record.getName()).getParent())
												.toList()));
				pending.remove(next);
				recordTables.put(next.getName().toUpperCase(Locale.ROOT),
						recordTable(schema, next, configuration.mapping(next.getName()), recordTables, tableLogs));
			}
			validateConfiguration();
			if (schema.getTables().isEmpty()) {
				throw new CommandException("No configured COBOL records were found.");
			}
			return new BuildResult(schema, tableLogs, warnings, controlRecords);
		}

		/** Storage layout of a control record's elementary items, by item path. */
		private Map<String, Object> controlLayout(CobolItem record) {
			Map<String, Object> fields = new LinkedHashMap<>();
			controlFields(record, record, fields);
			return mapOf("recordLength", record.getTotalLength(), "fields", fields);
		}

		private void controlFields(CobolItem record, CobolItem item, Map<String, Object> fields) {
			if (!selected(item)) {
				return;
			}
			if (item.getOccursMaximum() != null) {
				if (!item.isFiller()) {
					warnings.add("OCCURS items of a control record are not available for checks: " + item.getPath());
				}
				return;
			}
			if (item.isGroup()) {
				for (CobolItem child : item.getChildren()) {
					controlFields(record, child, fields);
				}
			} else if (!item.isFiller()) {
				fields.put(item.getPath(), mapOf("storage", storage(item, record), "conversion", conversion(item)));
			}
		}

		/**
		 * What a reader needs to decode the bytes of an item, relative to {@code base}.
		 */
		private static Map<String, Object> storage(CobolItem item, CobolItem base) {
			Map<String, Object> storage = mapOf("offset", item.getOffset() - base.getOffset(), "length",
					item.getLength(), "usage", CobolItem.usageText(item.getUsage()));
			if (item.getCategory() == Category.NUMERIC) {
				storage.put("digits", item.getDigits());
				storage.put("scale", item.getScale());
				storage.put("signed", item.isSigned());
				if (item.isSigned() && item.getUsage() == Usage.DISPLAY) {
					storage.put("signPosition", item.isSignLeading() ? "LEADING" : "TRAILING");
					storage.put("signSeparate", item.isSignSeparate());
				}
			}
			return storage;
		}

		/** COBOL details for review. */
		private static Map<String, Object> conversion(CobolItem item) {
			Map<String, Object> conversion = mapOf("cobolName", item.getName(), "category", item.getCategory().name(),
					"picture", item.getPicture());
			if (item.getRedefines() != null || !item.getRedefinedBy().isEmpty()) {
				conversion.put("redefinition", true);
			}
			if (!item.getConditions().isEmpty()) {
				conversion.put("conditions", item.getConditions().stream()
						.map(condition -> mapOf("name", condition.name(), "values", condition.values())).toList());
			}
			return conversion;
		}

		private Table recordTable(Schema schema, CobolItem record, RecordMapping mapping,
				Map<String, Table> recordTables, List<Map<String, Object>> tableLogs) {
			String tableName = valueOr(mapping.getTableName(), sqlName(record.getName()));
			if (schema.getTables().contains(tableName)) {
				throw new CommandException(
						"Table name conflicts: " + tableName + "; set tableName for " + record.getName());
			}
			Table table = new Table(tableName);
			table.setRemarks(record.getRemarks());
			schema.getTables().add(table);
			Table parent = mapping.getParent() == null ? null
					: recordTables.get(mapping.getParent().toUpperCase(Locale.ROOT));
			List<Column> inheritedKeys = new ArrayList<>();
			List<Column> parentKeys = List.of();
			Column sequence = null;
			if (parent != null) {
				if (!mapping.getPrimaryKey().isEmpty()) {
					throw new CommandException("primaryKey of a record with a parent is the parent key and a sequence"
							+ " number; remove primaryKey: record=" + record.getName());
				}
				parentKeys = parent.getPrimaryKeyConstraint().getColumns().toColumns();
				for (Column parentKey : parentKeys) {
					Column inherited = parentKey.clone().setIdentity(false).setDefaultValue(null);
					table.getColumns().add(inherited);
					inheritedKeys.add(inherited);
				}
				String sequenceName = valueOr(mapping.getSequenceColumn(),
						configuration.occurrenceColumnName(sqlName(record.getName())));
				if (table.getColumns().contains(sequenceName)) {
					throw new CommandException("Sequence column conflicts with an inherited key: table=" + tableName
							+ ", column=" + sequenceName + "; set sequenceColumn for " + record.getName());
				}
				sequence = new Column(sequenceName).setDataType(DataType.INT).setNotNull(true);
				sequence.setRemarks("Sequence of " + record.getName() + " records after their " + mapping.getParent()
						+ " record (1..)");
				table.getColumns().add(sequence);
			}
			List<CobolItem> items = new ArrayList<>();
			if (record.isGroup()) {
				addColumns(table, record, record, new ArrayList<>(), items);
			} else {
				addColumn(table, record, record, List.of(), items);
			}
			List<Column> primaryKey;
			if (parent != null) {
				primaryKey = new ArrayList<>(inheritedKeys);
				primaryKey.add(sequence);
			} else {
				primaryKey = columns(table, mapping.getPrimaryKey(), "primary key");
				if (primaryKey.isEmpty()) {
					throw new CommandException("primaryKey is required: record=" + record.getName());
				}
			}
			table.setPrimaryKey("PK_" + table.getName(), primaryKey.toArray(Column[]::new));
			if (parent != null) {
				table.getConstraints().addForeignKeyConstraint("FK_" + table.getName() + "_" + parent.getName(),
						inheritedKeys.toArray(Column[]::new), parentKeys.toArray(Column[]::new));
			}
			Map<String, Object> storage = mapOf("recordLength", record.getTotalLength(), "variableLength",
					hasDependingOn(record));
			// A record with a parent is a child data set numbered in file order, like an
			// OCCURS element.
			Map<String, Object> occurrence = parent == null ? null
					: mapOf("column", sequence.getName(), "parentRecord", mapping.getParent(), "recordSequence", true);
			tableLogs.add(tableLog(table, record, record, items, occurrence, storage));
			childTables(schema, table, record, record, tableLogs);
			return table;
		}

		private static Set<String> upper(List<String> values) {
			Set<String> result = new HashSet<>();
			values.forEach(value -> result.add(value.toUpperCase(Locale.ROOT)));
			return result;
		}

		private void selectRedefines(CobolItem item) {
			if (!item.getRedefinedBy().isEmpty()) {
				List<CobolItem> alternatives = new ArrayList<>();
				alternatives.add(item);
				alternatives.addAll(item.getRedefinedBy());
				CobolItem selected = item;
				String key = configurationKey(configuration.getRedefines(), item);
				if (key != null) {
					usedRedefines.add(key);
					String name = configuration.getRedefines().get(key);
					selected = alternatives.stream().filter(alternative -> alternative.getName().equalsIgnoreCase(name))
							.findFirst()
							.orElseThrow(() -> new CommandException(
									"redefines selects " + name + ", which is not an alternative of " + item.getPath()
											+ ": " + alternatives.stream().map(CobolItem::getName).toList()));
				}
				for (CobolItem alternative : alternatives) {
					selectedAlternatives.put(alternative, selected);
					if (alternative != selected) {
						warnings.add("REDEFINES alternative is not imported: " + alternative.getPath() + " (selected "
								+ selected.getName() + "; change with redefines: {" + item.getName() + ": "
								+ alternative.getName() + "})");
					}
				}
			}
			for (CobolItem child : item.getChildren()) {
				selectRedefines(child);
			}
		}

		private static String configurationKey(Map<String, String> values, CobolItem item) {
			for (String key : values.keySet()) {
				if (key.equalsIgnoreCase(item.getPath()) || key.equalsIgnoreCase(item.getName())) {
					return key;
				}
			}
			return null;
		}

		/**
		 * False for unselected REDEFINES alternatives; FILLER groups are imported for
		 * their named items.
		 */
		private boolean selected(CobolItem item) {
			CobolItem selected = selectedAlternatives.get(item);
			return selected == null || selected == item;
		}

		private void validateConfiguration() {
			for (String key : configuration.getRedefines().keySet()) {
				if (!usedRedefines.contains(key)) {
					throw new CommandException("redefines names an item that is not redefined: " + key);
				}
			}
			for (String key : configuration.getColumnNames().keySet()) {
				if (!usedColumnNames.contains(key)) {
					throw new CommandException("columnNames names an unknown imported item path: " + key);
				}
			}
			for (String key : configuration.getGeneratedTableNames().keySet()) {
				if (!usedTableNames.contains(key)) {
					throw new CommandException("generatedTableNames names an unknown OCCURS item path: " + key);
				}
			}
		}

		private void childTables(Schema schema, Table parentTable, CobolItem base, CobolItem group,
				List<Map<String, Object>> tableLogs) {
			for (CobolItem child : group.getChildren()) {
				if (!selected(child)) {
					continue;
				}
				if (child.getOccursMaximum() != null) {
					if (child.isFiller()) {
						if (hasNamedItems(child)) {
							warnings.add("OCCURS FILLER with named items is not imported: " + child.getPath());
						}
						continue;
					}
					occursTable(schema, parentTable, base, child, tableLogs);
				} else if (child.isGroup()) {
					childTables(schema, parentTable, base, child, tableLogs);
				}
			}
		}

		private void occursTable(Schema schema, Table parent, CobolItem parentBase, CobolItem array,
				List<Map<String, Object>> tableLogs) {
			String tableName = tableName(array);
			if (schema.getTables().contains(tableName)) {
				throw new CommandException("Generated table name conflicts: " + tableName
						+ "; set generatedTableNames for " + array.getPath());
			}
			Table table = new Table(tableName);
			table.setRemarks(array.getRemarks());
			schema.getTables().add(table);
			List<Column> parentKeys = parent.getPrimaryKeyConstraint().getColumns().toColumns();
			List<Column> inheritedKeys = new ArrayList<>();
			for (Column parentKey : parentKeys) {
				Column inherited = parentKey.clone().setIdentity(false).setDefaultValue(null);
				table.getColumns().add(inherited);
				inheritedKeys.add(inherited);
			}
			String occurrenceName = configuration.occurrenceColumnName(sqlName(array.getName()));
			Column occurrence = new Column(occurrenceName).setDataType(DataType.INT).setNotNull(true);
			occurrence.setRemarks("COBOL OCCURS occurrence number (1.." + array.getOccursMaximum() + ")");
			table.getColumns().add(occurrence);
			List<CobolItem> items = new ArrayList<>();
			if (array.isGroup()) {
				addColumns(table, array, array, new ArrayList<>(), items);
			} else {
				addColumn(table, array, array, List.of(), items);
			}
			List<Column> primaryKey = new ArrayList<>(inheritedKeys);
			primaryKey.add(occurrence);
			table.setPrimaryKey("PK_" + table.getName(), primaryKey.toArray(Column[]::new));
			table.getConstraints().addForeignKeyConstraint("FK_" + table.getName() + "_" + parent.getName(),
					inheritedKeys.toArray(Column[]::new), parentKeys.toArray(Column[]::new));
			// Same keys as the PL/I import: offset and length of the whole array, and one
			// element's length.
			Map<String, Object> occurrenceLog = mapOf("maximum", array.getOccursMaximum(), "column", occurrenceName,
					"minimum", array.getOccursMinimum(), "offset", array.getOffset() - parentBase.getOffset(), "length",
					array.getTotalLength(), "elementLength", array.getLength());
			if (array.getDependingOn() != null) {
				occurrenceLog.put("dependingOn", array.getDependingOn().getPath());
			}
			tableLogs.add(tableLog(table, array, array, items, occurrenceLog, null));
			childTables(schema, table, array, array, tableLogs);
		}

		private String tableName(CobolItem array) {
			for (Map.Entry<String, String> entry : configuration.getGeneratedTableNames().entrySet()) {
				if (entry.getKey().equalsIgnoreCase(array.getPath())) {
					usedTableNames.add(entry.getKey());
					return entry.getValue();
				}
			}
			return sqlName(array.getName());
		}

		private void addColumns(Table table, CobolItem base, CobolItem group, List<String> groupRemarks,
				List<CobolItem> items) {
			List<String> remarks = new ArrayList<>(groupRemarks);
			if (group != base && group.getRemarks() != null && configuration.isPrefixGroupRemarks()) {
				remarks.add(group.getRemarks());
			}
			for (CobolItem child : group.getChildren()) {
				if (!selected(child) || child.getOccursMaximum() != null || child.isFiller() && !child.isGroup()) {
					continue;
				}
				if (child.isGroup()) {
					addColumns(table, base, child, remarks, items);
				} else {
					addColumn(table, base, child, remarks, items);
				}
			}
		}

		private void addColumn(Table table, CobolItem base, CobolItem item, List<String> groupRemarks,
				List<CobolItem> items) {
			String name = columnName(item);
			if (table.getColumns().contains(name)) {
				throw new CommandException("Flattened COBOL column name conflicts: table=" + table.getName()
						+ ", column=" + name + ", item=" + item.getPath() + "; set columnNames for one of the items");
			}
			Column column = toColumn(name, item);
			List<String> remarks = new ArrayList<>(groupRemarks);
			if (item.getRemarks() != null && !item.getRemarks().isBlank()) {
				remarks.add(item.getRemarks());
			}
			if (!remarks.isEmpty()) {
				column.setRemarks(String.join(" / ", remarks));
			}
			table.getColumns().add(column);
			items.add(item);
			columnByItem.put(item, column);
		}

		private String columnName(CobolItem item) {
			for (Map.Entry<String, String> entry : configuration.getColumnNames().entrySet()) {
				if (entry.getKey().equalsIgnoreCase(item.getPath())) {
					usedColumnNames.add(entry.getKey());
					return entry.getValue();
				}
			}
			return sqlName(item.getName());
		}

		private Column toColumn(String name, CobolItem item) {
			Column column = new Column(name);
			switch (item.getCategory()) {
			case ALPHANUMERIC -> column.setDataType(DataType.CHAR).setLength(item.getDigits());
			case ALPHANUMERIC_EDITED, NUMERIC_EDITED -> {
				column.setDataType(DataType.CHAR).setLength(item.getLength());
				warnings.add("Edited PICTURE is imported as CHAR(" + item.getLength() + "): " + item.getPath());
			}
			case NATIONAL -> column.setDataType(DataType.NCHAR).setLength(item.getDigits());
			case FLOATING_POINT ->
				column.setDataType(item.getUsage() == Usage.FLOAT_LONG ? DataType.DOUBLE : DataType.REAL);
			case INDEX -> column.setDataType(DataType.INT);
			case NUMERIC -> numeric(column, item);
			case GROUP -> throw new IllegalStateException(item.getPath());
			}
			return column;
		}

		private static void numeric(Column column, CobolItem item) {
			int digits = item.getDigits();
			int scale = item.getScale();
			if (scale < 0) {
				column.setDataType(DataType.DECIMAL).setLength(digits - scale).setScale(0);
				return;
			}
			if (scale == 0 && (item.getUsage() == Usage.BINARY || item.getUsage() == Usage.NATIVE_BINARY)) {
				if (item.getUsage() == Usage.NATIVE_BINARY) {
					// COMP-5 holds the full binary range of its storage.
					int bytes = item.getLength();
					boolean signed = item.isSigned();
					if (bytes == 2) {
						column.setDataType(signed ? DataType.SMALLINT : DataType.INT);
					} else if (bytes == 4) {
						column.setDataType(signed ? DataType.INT : DataType.BIGINT);
					} else if (signed) {
						column.setDataType(DataType.BIGINT);
					} else {
						column.setDataType(DataType.DECIMAL).setLength(20).setScale(0);
					}
				} else if (digits <= 4) {
					column.setDataType(DataType.SMALLINT);
				} else if (digits <= 9) {
					column.setDataType(DataType.INT);
				} else {
					column.setDataType(DataType.BIGINT);
				}
				return;
			}
			column.setDataType(DataType.DECIMAL).setLength(Math.max(digits, scale)).setScale(scale);
		}

		private List<Column> columns(Table table, List<String> names, String type) {
			List<Column> result = new ArrayList<>();
			for (String name : names) {
				Column column = table.getColumns().get(name);
				if (column == null) {
					column = table.getColumns().get(sqlName(name));
				}
				if (column == null) {
					throw new CommandException(
							"Unknown " + type + " column: table=" + table.getName() + ", column=" + name);
				}
				result.add(column);
			}
			return result;
		}

		private static boolean hasNamedItems(CobolItem item) {
			return item.getChildren().stream().anyMatch(child -> !child.isFiller() || hasNamedItems(child));
		}

		private static boolean hasDependingOn(CobolItem item) {
			if (item.getDependingOn() != null) {
				return true;
			}
			return item.getChildren().stream().anyMatch(Builder::hasDependingOn);
		}

		private Map<String, Object> tableLog(Table table, CobolItem source, CobolItem base, List<CobolItem> items,
				Map<String, Object> occurrence, Map<String, Object> tableStorage) {
			List<Map<String, Object>> columns = new ArrayList<>();
			for (CobolItem item : items) {
				Column column = columnByItem.get(item);
				Map<String, Object> storage = storage(item, base);
				Map<String, Object> conversion = conversion(item);
				// The staging column is named after the source name, so it must be an SQL
				// identifier.
				columns.add(mapOf("name", column.getName(), "sourceName", column.getName(), "sourcePath",
						item.getPath(), "level", item.getLevel(), "sourceType", item.getCategory().name(),
						"declaration", item.getDeclaration(), "sourceLength", item.getDigits(), "sourceScale",
						item.getScale(), "dataType", column.getDataType().name(), "length", column.getLength(), "scale",
						column.getScale(), "remarks", column.getRemarks(), "storage", storage, "conversion",
						conversion));
			}
			Map<String, Object> result = mapOf("sourcePath", source.getPath(), "declaration", root(source).getName(),
					"level", source.getLevel(), "table", table.getName(), "remarks", table.getRemarks(), "columns",
					columns, "primaryKey",
					table.getPrimaryKeyConstraint().getColumns().toColumns().stream().map(Column::getName).toList());
			if (tableStorage != null) {
				result.put("storage", tableStorage);
			}
			if (occurrence != null) {
				result.put("occurrence", occurrence);
			}
			return result;
		}

		private static CobolItem root(CobolItem item) {
			CobolItem current = item;
			while (current.getParent() != null) {
				current = current.getParent();
			}
			return current;
		}
	}

	private static Map<String, Object> mapOf(Object... values) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < values.length; i += 2) {
			map.put((String) values[i], values[i + 1]);
		}
		return map;
	}

	@Getter
	@Setter
	public static class ImportConfiguration {
		private int formatVersion = 1;
		private String schemaName;
		private CobolCopybookParser.SourceFormat sourceFormat = CobolCopybookParser.SourceFormat.FIXED;
		/** Text replacements, like COPY ... REPLACING. */
		private Map<String, String> replacing = new LinkedHashMap<>();
		/**
		 * Record name for a copybook without a level-01 entry; defaults to the file
		 * name.
		 */
		private String recordName;
		private List<String> includeRecords = new ArrayList<>();
		private List<String> excludeRecords = new ArrayList<>();
		private List<RecordMapping> records = new ArrayList<>();
		/**
		 * Table names of OCCURS items by item path, such as {@code CUSTOMER.ORDERS}.
		 */
		private Map<String, String> generatedTableNames = new LinkedHashMap<>();
		/** Column names by item path, for duplicate data names in different groups. */
		private Map<String, String> columnNames = new LinkedHashMap<>();
		/** Selected REDEFINES alternative by redefined item path or name. */
		private Map<String, String> redefines = new LinkedHashMap<>();
		private String occurrenceColumnNamePattern = "${groupName}_NO";
		private boolean prefixGroupRemarks = true;

		private RecordMapping mapping(String recordName) {
			return records.stream().filter(item -> recordName.equalsIgnoreCase(item.getName())).findFirst()
					.orElse(null);
		}

		private String occurrenceColumnName(String groupName) {
			return occurrenceColumnNamePattern.replace("${groupName}", groupName);
		}
	}

	@Getter
	@Setter
	public static class RecordMapping {
		private String name;
		private String tableName;
		private List<String> primaryKey = new ArrayList<>();
		/**
		 * Record whose most recent occurrence in the data set is the parent of this
		 * record, as with header and detail records. The table inherits the parent's
		 * key and is numbered by {@code sequenceColumn}.
		 */
		private String parent;
		/**
		 * Sequence column of a record with a parent; defaults to
		 * occurrenceColumnNamePattern.
		 */
		private String sequenceColumn;
		/**
		 * Control record, such as a trailer with record counts and totals: no table is
		 * created; its layout is kept in the mapping for extractCobolCsv's
		 * trailerChecks.
		 */
		private boolean control;
	}
}
