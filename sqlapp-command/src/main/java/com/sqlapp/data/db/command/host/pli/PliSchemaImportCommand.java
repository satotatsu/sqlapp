/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.host.pli;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.sqlapp.data.db.command.AbstractCommand;
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
 * Imports PL/I level declarations into a relational schema XML document.
 *
 * <p>
 * Each selected level-1 structure becomes a root table. Non-array groups are
 * flattened into their table. Each array becomes a child table that inherits
 * the parent primary key and adds a one-based occurrence column. Scalar arrays
 * become child tables with one value column.
 * </p>
 */
@Getter
@Setter
public class PliSchemaImportCommand extends AbstractCommand implements TargetFileProperty, OutputDirectoryProperty {

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
			PliImportConfiguration configuration = new YamlConverter().fromJsonString(configurationFile,
					PliImportConfiguration.class);
			validate(configuration);
			String source = Files.readString(targetFile.toPath(), Charset.forName(encoding));
			PliDeclarationParser.ParseResult parseResult = new PliDeclarationParser(configuration.getCommentPlacement(),
					configuration.getMargins()).parse(source);
			BuildResult buildResult = new Builder(configuration, parseResult).build();
			ensureDirectory(outputDirectory, "output");
			String xmlName = outputFileName;
			if (xmlName == null || xmlName.isBlank()) {
				xmlName = baseName(targetFile.getName()) + ".xml";
			}
			File outputFile = new File(outputDirectory, xmlName);
			buildResult.schema.writeXml(outputFile);
			info("Output PL/I schema XML: " + outputFile.getAbsolutePath());
			for (String warning : buildResult.warnings) {
				info("WARNING: " + warning);
			}
			if (migrationMappingEnabled) {
				writeMigrationMapping(outputFile, buildResult, configuration);
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
		if (encoding == null || !Charset.isSupported(encoding)) {
			throw new CommandException("Unsupported encoding: " + encoding);
		}
	}

	private void validate(PliImportConfiguration configuration) {
		if (configuration == null) {
			throw new CommandException("PL/I import configuration is empty: " + configurationFile);
		}
		if (configuration.getFormatVersion() != PliImportConfiguration.FORMAT_VERSION) {
			throw new CommandException("Unsupported PL/I import formatVersion: " + configuration.getFormatVersion()
					+ ". Supported: " + PliImportConfiguration.FORMAT_VERSION);
		}
		if (blank(configuration.getSchemaName())) {
			throw new CommandException("schemaName is required.");
		}
		List<Integer> margins = configuration.getMargins();
		if (margins != null && !margins.isEmpty()) {
			if (margins.size() != 2 || margins.get(0) == null || margins.get(1) == null || margins.get(0) < 1
					|| margins.get(1) < margins.get(0)) {
				throw new CommandException("margins must be [left, right] with 1 <= left <= right: " + margins);
			}
		}
		if (blank(configuration.getOccurrenceColumnNamePattern())) {
			throw new CommandException("occurrenceColumnNamePattern must not be blank.");
		}
	}

	private void writeMigrationMapping(File outputFile, BuildResult result, PliImportConfiguration configuration) {
		File directory = migrationMappingDirectory != null ? migrationMappingDirectory : outputDirectory;
		ensureDirectory(directory, "migration mapping");
		String name = migrationMappingFileName;
		if (name == null || name.isBlank()) {
			name = baseName(targetFile.getName()) + "-legacy-migration.yaml";
		}
		File file = new File(directory, name);
		var mapping = new LegacyMigrationMappingBuilder().buildPliImportMapping(targetFile, outputFile, encoding,
				configuration.getSchemaName(), result.tableLogs, result.warnings);
		new LegacyMigrationMappingIO().write(file, mapping);
		info("Output legacy migration mapping: " + file.getAbsolutePath());
	}

	private void ensureDirectory(File directory, String type) {
		if (directory.exists()) {
			if (!directory.isDirectory()) {
				throw new CommandException("The " + type + " path is not a directory: " + directory);
			}
			return;
		}
		if (!directory.mkdirs()) {
			throw new CommandException("Failed to create " + type + " directory: " + directory);
		}
	}

	private static String baseName(String name) {
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private static String key(String value) {
		return value.trim().toUpperCase(Locale.ROOT);
	}

	private static <T> List<T> list(List<T> value) {
		return value == null ? List.of() : value;
	}

	private static Map<String, String> normalizedPaths(Map<String, String> values, String property) {
		Map<String, String> result = new LinkedHashMap<>();
		if (values == null) {
			return result;
		}
		values.forEach((path, value) -> {
			if (blank(path) || blank(value)) {
				throw new CommandException(property + " must not contain blank keys or values: " + path);
			}
			if (result.put(key(path), value.trim()) != null) {
				throw new CommandException(property + " contains a duplicate path: " + path);
			}
		});
		return result;
	}

	private record BuildResult(Schema schema, List<Map<String, Object>> tableLogs, List<String> warnings) {
	}

	/**
	 * Builds the schema for one command execution.
	 */
	private static final class Builder {
		private final PliImportConfiguration configuration;
		private final PliDeclarationParser.ParseResult parsed;
		private final Schema schema;
		private final List<Map<String, Object>> tableLogs = new ArrayList<>();
		private final List<String> warnings;
		private final Map<String, String> tableNames = new HashMap<>();
		private final Set<String> excludedItems = new LinkedHashSet<>();
		private final Map<String, String> generatedTableNames;
		private final Map<String, String> columnNames;
		private final Map<String, String> occurrenceColumnNames;

		private Builder(PliImportConfiguration configuration, PliDeclarationParser.ParseResult parsed) {
			this.configuration = configuration;
			this.parsed = parsed;
			this.schema = new Schema(configuration.getSchemaName());
			this.warnings = new ArrayList<>(parsed.warnings());
			for (String item : list(configuration.getExcludeItems())) {
				if (blank(item)) {
					throw new CommandException("excludeItems must not contain blank paths.");
				}
				excludedItems.add(key(item));
			}
			this.generatedTableNames = normalizedPaths(configuration.getGeneratedTableNames(), "generatedTableNames");
			this.columnNames = normalizedPaths(configuration.getColumnNames(), "columnNames");
			this.occurrenceColumnNames = normalizedPaths(configuration.getOccurrenceColumnNames(),
					"occurrenceColumnNames");
		}

		private BuildResult build() {
			List<Selected> selected = select();
			validateItemPaths(selected);
			for (Selected selection : selected) {
				buildRoot(selection.declaration, selection.structure);
			}
			if (schema.getTables().isEmpty()) {
				throw new CommandException("No configured PL/I declarations were found.");
			}
			return new BuildResult(schema, tableLogs, warnings);
		}

		private record Selected(PliItem declaration, PliImportConfiguration.Structure structure) {
		}

		private List<Selected> select() {
			Map<String, List<PliItem>> declarations = new LinkedHashMap<>();
			for (PliItem declaration : parsed.declarations()) {
				if (!declaration.isFiller()) {
					declarations.computeIfAbsent(declaration.name, k -> new ArrayList<>()).add(declaration);
				}
			}
			Set<String> includes = names(configuration.getIncludeDeclarations(), "includeDeclarations", declarations);
			Set<String> excludes = names(configuration.getExcludeDeclarations(), "excludeDeclarations", declarations);
			for (String include : includes) {
				if (excludes.contains(include)) {
					throw new CommandException("Declaration is both included and excluded: " + include);
				}
			}
			Map<String, PliImportConfiguration.Structure> structures = new LinkedHashMap<>();
			for (PliImportConfiguration.Structure structure : list(configuration.getStructures())) {
				if (structure == null || blank(structure.getName())) {
					throw new CommandException("structures[].name is required.");
				}
				String name = key(structure.getName());
				if (structures.put(name, structure) != null) {
					throw new CommandException("Duplicate structure configuration: " + structure.getName());
				}
				if (!declarations.containsKey(name)) {
					throw new CommandException("Configured structure was not found in the PL/I source: "
							+ structure.getName() + ". Declarations: " + declarations.keySet());
				}
				if (excludes.contains(name) || !includes.isEmpty() && !includes.contains(name)) {
					throw new CommandException(
							"Configured structure is not selected by includeDeclarations/excludeDeclarations: "
									+ structure.getName());
				}
			}
			List<Selected> result = new ArrayList<>();
			for (Map.Entry<String, List<PliItem>> entry : declarations.entrySet()) {
				String name = entry.getKey();
				if (excludes.contains(name) || !includes.isEmpty() && !includes.contains(name)) {
					continue;
				}
				PliImportConfiguration.Structure structure = structures.get(name);
				if (structure == null) {
					if (!includes.isEmpty()) {
						throw new CommandException("No structures entry was configured for included declaration: "
								+ name + ". Add it to structures with a primaryKey.");
					}
					warnings.add("No structure mapping was provided: " + name);
					continue;
				}
				if (entry.getValue().size() > 1) {
					throw new CommandException("PL/I declaration is defined more than once: " + name);
				}
				result.add(new Selected(entry.getValue().getFirst(), structure));
			}
			return result;
		}

		private Set<String> names(List<String> values, String property, Map<String, List<PliItem>> declarations) {
			Set<String> result = new LinkedHashSet<>();
			for (String value : list(values)) {
				if (blank(value)) {
					throw new CommandException(property + " must not contain blank names.");
				}
				String name = key(value);
				if (!declarations.containsKey(name)) {
					throw new CommandException(property + " refers to an unknown declaration: " + value
							+ ". Declarations: " + declarations.keySet());
				}
				result.add(name);
			}
			return result;
		}

		private void validateItemPaths(List<Selected> selected) {
			Map<String, PliItem> items = new HashMap<>();
			for (Selected selection : selected) {
				collect(selection.declaration, items);
			}
			for (String path : excludedItems) {
				PliItem item = items.get(path);
				if (item == null) {
					throw new CommandException("excludeItems refers to an unknown item path: " + path);
				}
				if (item.parent == null) {
					throw new CommandException("Use excludeDeclarations to exclude a level-1 declaration: " + path);
				}
			}
			for (String path : generatedTableNames.keySet()) {
				requireArray(items.get(path), path, "generatedTableNames");
			}
			for (String path : occurrenceColumnNames.keySet()) {
				requireArray(items.get(path), path, "occurrenceColumnNames");
			}
			for (String path : columnNames.keySet()) {
				PliItem item = items.get(path);
				if (item == null || item.type == null || item.isFiller()) {
					throw new CommandException("columnNames must refer to a named scalar item: " + path);
				}
			}
		}

		private void requireArray(PliItem item, String path, String property) {
			if (item == null || !item.isArray()) {
				throw new CommandException(property + " must refer to an array item: " + path);
			}
		}

		private void collect(PliItem item, Map<String, PliItem> items) {
			items.putIfAbsent(key(item.path()), item);
			for (PliItem child : item.children) {
				collect(child, items);
			}
		}

		private void buildRoot(PliItem declaration, PliImportConfiguration.Structure structure) {
			if (!declaration.errors.isEmpty()) {
				throw new CommandException(
						"Invalid PL/I declaration " + declaration.name + ": " + String.join(" ", declaration.errors));
			}
			if (declaration.type != null) {
				throw new CommandException("Level-1 item " + declaration.location()
						+ " is not a structure. Only structures can be imported.");
			}
			if (declaration.isArray()) {
				throw new CommandException("Level-1 array declarations are not supported: " + declaration.location()
						+ ". Declare a level-1 structure that contains the array, for example DCL 1 " + declaration.name
						+ "_TABLE, 2 " + declaration.name + declaration.dimension() + ", ...");
			}
			requireSupported(declaration);
			String layoutReason = PliStorageLayout.compute(declaration);
			if (layoutReason != null) {
				warnings.add("Storage offsets were not calculated for " + declaration.name + " because " + layoutReason
						+ ". Declare the structure UNALIGNED or take offsets from the compiler "
						+ "AGGREGATE listing.");
			}
			Table table = new Table(
					blank(structure.getTableName()) ? declaration.name : structure.getTableName().trim());
			registerTable(table.getName(), declaration, "structures[].tableName");
			table.setRemarks(declaration.remarks);
			schema.getTables().add(table);
			List<Map<String, Object>> columnLogs = new ArrayList<>();
			addColumns(table, declaration, new ArrayList<>(), columnLogs);
			List<Column> primaryKey = new ArrayList<>();
			for (String name : list(structure.getPrimaryKey())) {
				Column column = blank(name) ? null : findColumn(table, name);
				if (column == null) {
					throw new CommandException("Unknown primary key column: table=" + table.getName() + ", column="
							+ name + ". Columns: " + table.getColumns().stream().map(Column::getName).toList());
				}
				if (primaryKey.contains(column)) {
					throw new CommandException(
							"Duplicate primary key column: table=" + table.getName() + ", column=" + name);
				}
				primaryKey.add(column);
			}
			if (primaryKey.isEmpty()) {
				throw new CommandException("primaryKey is required: structure=" + declaration.name);
			}
			for (Column column : primaryKey) {
				column.setNotNull(true);
			}
			table.setPrimaryKey("PK_" + table.getName(), primaryKey.toArray(Column[]::new));
			Map<String, Object> log = tableLog(table, declaration, columnLogs);
			if (declaration.layoutResolved) {
				log.put("storage", mapOf("recordLength", bytes(declaration.sizeBits)));
			}
			tableLogs.add(log);
			addChildTables(table, declaration);
		}

		private void buildArrayTable(Table parent, PliItem array) {
			if (array.isFiller()) {
				throw new CommandException("Unnamed (*) arrays cannot be imported: " + array.location()
						+ ". Name the array or exclude its parent with excludeItems.");
			}
			requireSupported(array);
			String path = key(array.path());
			String tableName = generatedTableNames.getOrDefault(path, array.name);
			registerTable(tableName, array, "generatedTableNames");
			Table table = new Table(tableName);
			table.setRemarks(array.remarks);
			schema.getTables().add(table);
			List<Column> parentKeys = parent.getPrimaryKeyConstraint().getColumns().toColumns();
			List<Column> inheritedKeys = new ArrayList<>();
			for (Column parentKey : parentKeys) {
				Column inherited = parentKey.clone().setIdentity(false).setDefaultValue(null);
				table.getColumns().add(inherited);
				inheritedKeys.add(inherited);
			}
			String occurrenceName = occurrenceColumnNames.get(path);
			if (occurrenceName == null) {
				occurrenceName = configuration.getOccurrenceColumnNamePattern().replace("${groupName}", array.name)
						.replace("${tableName}", tableName);
			}
			if (findColumn(table, occurrenceName) != null) {
				throw new CommandException("Occurrence column name conflicts with an inherited key: table=" + tableName
						+ ", column=" + occurrenceName + ". Set occurrenceColumnNames for " + array.path() + ".");
			}
			Column occurrence = new Column(occurrenceName).setDataType(DataType.INT).setNotNull(true);
			occurrence.setRemarks("PL/I array occurrence number (1.." + array.occurs + ")");
			table.getColumns().add(occurrence);
			List<Map<String, Object>> columnLogs = new ArrayList<>();
			if (array.type != null) {
				addScalarColumn(table, array, List.of(), columnLogs);
			} else {
				addColumns(table, array, new ArrayList<>(), columnLogs);
			}
			List<Column> primaryKey = new ArrayList<>(inheritedKeys);
			primaryKey.add(occurrence);
			table.setPrimaryKey("PK_" + table.getName(), primaryKey.toArray(Column[]::new));
			table.getConstraints().addForeignKeyConstraint("FK_" + table.getName() + "_" + parent.getName(),
					inheritedKeys.toArray(Column[]::new), parentKeys.toArray(Column[]::new));
			Map<String, Object> log = tableLog(table, array, columnLogs);
			Map<String, Object> occurrenceLog = mapOf("maximum", array.occurs, "column", occurrenceName, "lowerBound",
					array.lowerBound);
			if (array.root().layoutResolved) {
				occurrenceLog.putAll(storage(array.offsetBits, array.sizeBits, false));
				occurrenceLog.put("elementLength", bytes(array.elementBits));
				if (array.elementBits % 8 != 0) {
					occurrenceLog.put("elementBitLength", array.elementBits);
				}
			}
			log.put("occurrence", occurrenceLog);
			tableLogs.add(log);
			if (array.type == null) {
				addChildTables(table, array);
			}
		}

		private void addChildTables(Table parentTable, PliItem group) {
			for (PliItem child : group.children) {
				if (excluded(child)) {
					continue;
				}
				if (child.isArray()) {
					buildArrayTable(parentTable, child);
				} else if (child.type == null) {
					addChildTables(parentTable, child);
				}
			}
		}

		private void addColumns(Table table, PliItem group, List<String> groupRemarks,
				List<Map<String, Object>> columnLogs) {
			for (PliItem child : group.children) {
				if (excluded(child) || child.isArray()) {
					continue;
				}
				if (child.type == null) {
					requireSupported(child);
					List<String> remarks = new ArrayList<>(groupRemarks);
					if (configuration.isPrefixGroupRemarks() && !blank(child.remarks)) {
						remarks.add(child.remarks);
					}
					addColumns(table, child, remarks, columnLogs);
				} else if (!child.isFiller()) {
					addScalarColumn(table, child, groupRemarks, columnLogs);
				}
			}
		}

		private void addScalarColumn(Table table, PliItem item, List<String> groupRemarks,
				List<Map<String, Object>> columnLogs) {
			requireSupported(item);
			String name = columnNames.getOrDefault(key(item.path()), item.name);
			Column existing = findColumn(table, name);
			if (existing != null) {
				throw new CommandException("PL/I column name conflicts: table=" + table.getName() + ", column=" + name
						+ ", item=" + item.location() + ". Rename it with columnNames.");
			}
			Column column = item.type.toColumn(name, configuration.isBit1AsBoolean());
			List<String> remarks = new ArrayList<>(groupRemarks);
			if (!blank(item.remarks)) {
				remarks.add(item.remarks);
			}
			if (!remarks.isEmpty()) {
				column.setRemarks(String.join(" / ", remarks));
			}
			table.getColumns().add(column);
			Map<String, Object> log = mapOf("name", column.getName(), "sourceName", item.name, "sourcePath",
					item.path(), "level", item.level, "pliType", item.type.pliType(), "declaration", item.declaration(),
					"sourceLength", item.type.sourceLength(), "sourceScale", item.type.sourceScale(), "dataType",
					column.getDataType().name(), "length", column.getLength(), "scale", column.getScale(), "remarks",
					column.getRemarks());
			if (item.root().layoutResolved) {
				long offset = item.isArray() ? 0 : item.offsetBits;
				long size = item.isArray() ? item.elementBits : item.sizeBits;
				log.put("storage", storage(offset, size, item.type.kind() == PliDataType.Kind.BIT));
			}
			columnLogs.add(log);
		}

		private Map<String, Object> storage(long offsetBits, long sizeBits, boolean bit) {
			Map<String, Object> result = mapOf("offset", offsetBits / 8, "length",
					PliDataType.roundUp(offsetBits % 8 + sizeBits, 8) / 8);
			if (bit || offsetBits % 8 != 0 || sizeBits % 8 != 0) {
				result.put("bitOffset", offsetBits % 8);
				result.put("bitLength", sizeBits);
			}
			return result;
		}

		private void requireSupported(PliItem item) {
			if (item.unsupported != null) {
				throw new CommandException("Unsupported PL/I item " + item.location() + ": " + item.unsupported
						+ ". Exclude it with excludeItems if it is not needed.");
			}
		}

		private boolean excluded(PliItem item) {
			return excludedItems.contains(key(item.path()));
		}

		private void registerTable(String name, PliItem item, String property) {
			String previous = tableNames.putIfAbsent(name.toUpperCase(Locale.ROOT), item.path());
			if (previous != null) {
				throw new CommandException("Table name conflicts: " + name + " (" + previous + " and " + item.path()
						+ "). Rename it with " + property + ".");
			}
		}

		private Column findColumn(Table table, String name) {
			for (Column column : table.getColumns()) {
				if (column.getName().equalsIgnoreCase(name.trim())) {
					return column;
				}
			}
			return null;
		}

		private Map<String, Object> tableLog(Table table, PliItem source, List<Map<String, Object>> columns) {
			return mapOf("sourcePath", source.path(), "declaration", source.root().name, "level", source.level, "table",
					table.getName(), "remarks", table.getRemarks(), "columns", columns, "primaryKey",
					table.getPrimaryKeyConstraint().getColumns().toColumns().stream().map(Column::getName).toList());
		}

		private static long bytes(long bits) {
			return PliDataType.roundUp(bits, 8) / 8;
		}

		private static Map<String, Object> mapOf(Object... values) {
			Map<String, Object> map = new LinkedHashMap<>();
			for (int i = 0; i < values.length; i += 2) {
				map.put((String) values[i], values[i + 1]);
			}
			return map;
		}
	}
}
