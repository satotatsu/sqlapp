/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.host.pli;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.migration.LegacyMigrationMappingBuilder;
import com.sqlapp.data.db.command.migration.LegacyMigrationMappingIO;
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
 */
@Getter
@Setter
public class PliSchemaImportCommand extends AbstractCommand
		implements TargetFileProperty, OutputDirectoryProperty {

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
			ParseResult parseResult = new Parser().parse(source);
			BuildResult buildResult = build(parseResult, configuration);
			ensureDirectory(outputDirectory, "output");
			String xmlName = outputFileName;
			if (xmlName == null || xmlName.isBlank()) {
				xmlName = baseName(targetFile.getName()) + ".xml";
			}
			File outputFile = new File(outputDirectory, xmlName);
			buildResult.schema.writeXml(outputFile);
			info("Output PL/I schema XML: " + outputFile.getAbsolutePath());
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
	}

	private BuildResult build(ParseResult parsed, ImportConfiguration configuration) {
		if (configuration.getSchemaName() == null || configuration.getSchemaName().isBlank()) {
			throw new CommandException("schemaName is required.");
		}
		Schema schema = new Schema(configuration.getSchemaName());
		List<Map<String, Object>> tableLogs = new ArrayList<>();
		List<String> warnings = new ArrayList<>(parsed.warnings);
		Set<String> includes = Set.copyOf(configuration.getIncludeDeclarations());
		Set<String> excludes = Set.copyOf(configuration.getExcludeDeclarations());
		for (PliItem declaration : parsed.declarations) {
			if (!includes.isEmpty() && !includes.contains(declaration.name) || excludes.contains(declaration.name)) {
				continue;
			}
			StructureMapping mapping = configuration.mapping(declaration.name);
			if (mapping == null) {
				warnings.add("No structure mapping was provided: " + declaration.name);
				continue;
			}
			Table table = new Table(valueOr(mapping.getTableName(), declaration.name));
			table.setRemarks(declaration.remarks);
			schema.getTables().add(table);
			addScalarColumns(table, declaration, configuration, new ArrayList<>(), warnings);
			List<Column> primaryKey = columns(table, mapping.getPrimaryKey(), "primary key");
			if (primaryKey.isEmpty()) {
				throw new CommandException("primaryKey is required: structure=" + declaration.name);
			}
			table.setPrimaryKey("PK_" + table.getName(), primaryKey.toArray(Column[]::new));
			tableLogs.add(tableLog(table, declaration, null));
			buildNestedArrayTables(schema, table, declaration, configuration, tableLogs, warnings);
		}
		if (schema.getTables().isEmpty()) {
			throw new CommandException("No configured PL/I declarations were found.");
		}
		return new BuildResult(schema, tableLogs, warnings);
	}

	private void buildArrayTable(Schema schema, Table parent, PliItem array, ImportConfiguration configuration,
			List<Map<String, Object>> tableLogs, List<String> warnings) {
		String tableName = configuration.getGeneratedTableNames().getOrDefault(array.path(), array.name);
		if (schema.getTables().contains(tableName)) {
			throw new CommandException("Generated table name conflicts: " + tableName);
		}
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
		String occurrenceName = configuration.occurrenceColumnName(array.name);
		Column occurrence = new Column(occurrenceName).setDataType(DataType.INT).setNotNull(true);
		occurrence.setRemarks("PL/I array occurrence number (1.." + array.occurs + ")");
		table.getColumns().add(occurrence);
		addScalarColumns(table, array, configuration, new ArrayList<>(), warnings);
		List<Column> primaryKey = new ArrayList<>(inheritedKeys);
		primaryKey.add(occurrence);
		table.setPrimaryKey("PK_" + table.getName(), primaryKey.toArray(Column[]::new));
		table.getConstraints().addForeignKeyConstraint("FK_" + table.getName() + "_" + parent.getName(),
				inheritedKeys.toArray(Column[]::new), parentKeys.toArray(Column[]::new));
		tableLogs.add(tableLog(table, array, occurrenceName));
		buildNestedArrayTables(schema, table, array, configuration, tableLogs, warnings);
	}

	private void buildNestedArrayTables(Schema schema, Table parentTable, PliItem group,
			ImportConfiguration configuration, List<Map<String, Object>> tableLogs, List<String> warnings) {
		for (PliItem child : group.children) {
			if (child.occurs != null) {
				buildArrayTable(schema, parentTable, child, configuration, tableLogs, warnings);
			} else if (child.type == null) {
				buildNestedArrayTables(schema, parentTable, child, configuration, tableLogs, warnings);
			}
		}
	}

	private void addScalarColumns(Table table, PliItem group, ImportConfiguration configuration,
			List<String> groupRemarks, List<String> warnings) {
		List<String> remarks = new ArrayList<>(groupRemarks);
		if (group != null && group.type == null && group.occurs == null && group.remarks != null
				&& configuration.isPrefixGroupRemarks()) {
			remarks.add(group.remarks);
		}
		for (PliItem child : group.children) {
			if (child.occurs != null) {
				continue;
			}
			if (child.type == null) {
				addScalarColumns(table, child, configuration, remarks, warnings);
				continue;
			}
			if (table.getColumns().contains(child.name)) {
				throw new CommandException("Flattened PL/I column name conflicts: table=" + table.getName()
						+ ", column=" + child.name);
			}
			Column column = toColumn(child, warnings);
			List<String> allRemarks = new ArrayList<>(remarks);
			if (child.remarks != null && !child.remarks.isBlank()) {
				allRemarks.add(child.remarks);
			}
			if (!allRemarks.isEmpty()) {
				column.setRemarks(String.join(" / ", allRemarks));
			}
			table.getColumns().add(column);
		}
	}

	private Column toColumn(PliItem item, List<String> warnings) {
		Column column = new Column(item.name);
		switch (item.type) {
		case "CHAR", "CHARACTER":
			column.setDataType(DataType.CHAR).setLength(item.length);
			break;
		case "BIT":
			column.setDataType(DataType.BINARY).setLength((item.length + 7L) / 8L);
			break;
		case "FIXED_BIN":
			if (item.precision <= 15) {
				column.setDataType(DataType.SMALLINT);
			} else if (item.precision <= 31) {
				column.setDataType(DataType.INT);
			} else {
				column.setDataType(DataType.BIGINT);
			}
			break;
		case "FIXED_DEC":
			column.setDataType(DataType.DECIMAL).setLength(item.precision).setScale(item.scale);
			break;
		case "GRAPHIC", "WIDECHAR":
			column.setDataType(DataType.NCHAR).setLength(item.length);
			break;
		default:
			throw new CommandException("Unsupported PL/I type: " + item.type + ", item=" + item.path());
		}
		return column;
	}

	private List<Column> columns(Table table, List<String> names, String type) {
		List<Column> result = new ArrayList<>();
		for (String name : names) {
			Column column = table.getColumns().get(name);
			if (column == null) {
				throw new CommandException("Unknown " + type + " column: table=" + table.getName() + ", column="
						+ name);
			}
			result.add(column);
		}
		return result;
	}

	private Map<String, Object> tableLog(Table table, PliItem source, String occurrenceColumn) {
		List<Map<String, Object>> columns = scalarItems(source).stream()
				.map(item -> {
					Column column = table.getColumns().get(item.name);
					return mapOf("name", column.getName(), "sourceName", item.name, "sourcePath", item.path(),
							"level", item.level, "pliType", item.type, "declaration", item.declaration(),
							"sourceLength", item.type != null && item.type.startsWith("FIXED_")
									? item.precision : item.length,
							"sourceScale", item.scale, "dataType", column.getDataType().name(),
							"length", column.getLength(), "scale", column.getScale(), "remarks",
							column.getRemarks());
				})
				.toList();
		Map<String, Object> result = mapOf("sourcePath", source.path(), "declaration", root(source).name,
				"level", source.level, "table", table.getName(), "remarks", table.getRemarks(),
				"columns", columns,
				"primaryKey",
				table.getPrimaryKeyConstraint().getColumns().toColumns().stream().map(Column::getName).toList());
		if (source.occurs != null) {
			result.put("occurrence", mapOf("maximum", source.occurs, "column", occurrenceColumn));
		}
		return result;
	}

	private List<PliItem> scalarItems(PliItem group) {
		List<PliItem> result = new ArrayList<>();
		for (PliItem child : group.children) {
			if (child.occurs != null) {
				continue;
			}
			if (child.type == null) {
				result.addAll(scalarItems(child));
			} else {
				result.add(child);
			}
		}
		return result;
	}

	private PliItem root(PliItem item) {
		PliItem current = item;
		while (current.parent != null) {
			current = current.parent;
		}
		return current;
	}

	private void writeMigrationMapping(File outputFile, BuildResult result, ImportConfiguration configuration) {
		File directory = migrationMappingDirectory != null ? migrationMappingDirectory : outputDirectory;
		ensureDirectory(directory, "migration mapping");
		String name = migrationMappingFileName;
		if (name == null || name.isBlank()) {
			name = baseName(targetFile.getName()) + "-legacy-migration.yaml";
		}
		File file = new File(directory, name);
		var mapping = new LegacyMigrationMappingBuilder().buildPliImportMapping(targetFile, outputFile,
				encoding, configuration.getSchemaName(), result.tableLogs, result.warnings);
		new LegacyMigrationMappingIO().write(file, mapping);
		info("Output legacy migration mapping: " + file.getAbsolutePath());
	}

	private void ensureDirectory(File directory, String type) {
		if (!directory.exists() && !directory.mkdirs()) {
			throw new CommandException("Failed to create " + type + " directory: " + directory);
		}
	}

	private String baseName(String name) {
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	private String valueOr(String value, String defaultValue) {
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private Map<String, Object> mapOf(Object... values) {
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
		private List<String> includeDeclarations = new ArrayList<>();
		private List<String> excludeDeclarations = new ArrayList<>();
		private List<StructureMapping> structures = new ArrayList<>();
		private Map<String, String> generatedTableNames = new LinkedHashMap<>();
		private String occurrenceColumnNamePattern = "${groupName}_NO";
		private boolean prefixGroupRemarks = true;

		private StructureMapping mapping(String declarationName) {
			return structures.stream().filter(item -> declarationName.equalsIgnoreCase(item.getName())).findFirst()
					.orElse(null);
		}

		private String occurrenceColumnName(String groupName) {
			return occurrenceColumnNamePattern.replace("${groupName}", groupName);
		}
	}

	@Getter
	@Setter
	public static class StructureMapping {
		private String name;
		private String tableName;
		private List<String> primaryKey = new ArrayList<>();
	}

	private static final class Parser {
		private static final Pattern ITEM = Pattern.compile("(?is)^(?:DCL|DECLARE)?\\s*(\\d+)\\s+"
				+ "([A-Z_$#@][A-Z0-9_$#@]*)(?:\\s*\\(\\s*(\\d+)\\s*\\))?\\s*(.*)$");
		private static final Pattern CHAR = Pattern.compile("(?is)^(CHAR|CHARACTER|BIT|GRAPHIC|WIDECHAR)"
				+ "\\s*\\(\\s*(\\d+)\\s*\\).*$");
		private static final Pattern FIXED_BIN = Pattern.compile("(?is)^FIXED\\s+(?:BIN|BINARY)"
				+ "\\s*\\(\\s*(\\d+)\\s*\\).*$");
		private static final Pattern FIXED_DEC = Pattern.compile("(?is)^FIXED\\s+(?:DEC|DECIMAL)"
				+ "\\s*\\(\\s*(\\d+)\\s*(?:,\\s*(-?\\d+)\\s*)?\\).*$");

		private ParseResult parse(String source) {
			List<String> warnings = new ArrayList<>();
			List<Part> parts = split(source);
			List<PliItem> declarations = new ArrayList<>();
			Deque<PliItem> stack = new ArrayDeque<>();
			PliItem last = null;
			for (Part part : parts) {
				if (part.comment) {
					if (last != null) {
						last.remarks = append(last.remarks, part.text.trim());
					}
					continue;
				}
				String text = part.text.trim();
				if (text.isEmpty()) {
					continue;
				}
				Matcher matcher = ITEM.matcher(text.toUpperCase(Locale.ROOT));
				if (!matcher.matches()) {
					warnings.add("Unsupported declaration fragment: " + text);
					continue;
				}
				PliItem item = new PliItem();
				item.level = Integer.parseInt(matcher.group(1));
				item.name = matcher.group(2);
				item.occurs = matcher.group(3) == null ? null : Integer.valueOf(matcher.group(3));
				parseType(item, matcher.group(4).trim(), text);
				while (!stack.isEmpty() && stack.peek().level >= item.level) {
					stack.pop();
				}
				if (stack.isEmpty()) {
					declarations.add(item);
				} else {
					item.parent = stack.peek();
					stack.peek().children.add(item);
				}
				stack.push(item);
				last = item;
			}
			return new ParseResult(declarations, warnings);
		}

		private void parseType(PliItem item, String attributes, String original) {
			if (attributes.isBlank() || attributes.equals("UNALIGNED") || attributes.equals("ALIGNED")) {
				return;
			}
			Matcher matcher = CHAR.matcher(attributes);
			if (matcher.matches()) {
				item.type = matcher.group(1).toUpperCase(Locale.ROOT);
				item.length = Long.valueOf(matcher.group(2));
				return;
			}
			matcher = FIXED_BIN.matcher(attributes);
			if (matcher.matches()) {
				item.type = "FIXED_BIN";
				item.precision = Integer.parseInt(matcher.group(1));
				return;
			}
			matcher = FIXED_DEC.matcher(attributes);
			if (matcher.matches()) {
				item.type = "FIXED_DEC";
				item.precision = Integer.parseInt(matcher.group(1));
				item.scale = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
				return;
			}
			throw new CommandException("Unsupported PL/I declaration attributes: " + original);
		}

		private List<Part> split(String source) {
			List<Part> parts = new ArrayList<>();
			StringBuilder current = new StringBuilder();
			int parentheses = 0;
			for (int i = 0; i < source.length(); i++) {
				if (i + 1 < source.length() && source.charAt(i) == '/' && source.charAt(i + 1) == '*') {
					addCode(parts, current);
					int end = source.indexOf("*/", i + 2);
					if (end < 0) {
						throw new CommandException("Unterminated PL/I comment.");
					}
					parts.add(new Part(source.substring(i + 2, end), true));
					i = end + 1;
					continue;
				}
				char character = source.charAt(i);
				if (character == '(') {
					parentheses++;
				} else if (character == ')') {
					parentheses--;
				}
				if ((character == ',' || character == ';') && parentheses == 0) {
					addCode(parts, current);
				} else {
					current.append(character);
				}
			}
			addCode(parts, current);
			return parts;
		}

		private void addCode(List<Part> parts, StringBuilder current) {
			if (!current.toString().isBlank()) {
				parts.add(new Part(current.toString(), false));
			}
			current.setLength(0);
		}

		private String append(String current, String addition) {
			return current == null || current.isBlank() ? addition : current + " " + addition;
		}
	}

	private record Part(String text, boolean comment) {
	}

	private static final class PliItem {
		private int level;
		private String name;
		private Integer occurs;
		private String type;
		private Long length;
		private int precision;
		private int scale;
		private String remarks;
		private PliItem parent;
		private final List<PliItem> children = new ArrayList<>();

		private String path() {
			return parent == null ? name : parent.path() + "." + name;
		}

		private String declaration() {
			return switch (type) {
			case "CHAR", "CHARACTER", "BIT", "GRAPHIC", "WIDECHAR" -> type + "(" + length + ")";
			case "FIXED_BIN" -> "FIXED BIN(" + precision + ")";
			case "FIXED_DEC" -> "FIXED DEC(" + precision + "," + scale + ")";
			case null -> occurs == null ? null : name + "(" + occurs + ")";
			default -> type;
			};
		}
	}

	private record ParseResult(List<PliItem> declarations, List<String> warnings) {
	}

	private record BuildResult(Schema schema, List<Map<String, Object>> tableLogs, List<String> warnings) {
	}
}
