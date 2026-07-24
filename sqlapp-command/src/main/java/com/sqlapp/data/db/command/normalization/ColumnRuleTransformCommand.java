/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.normalization;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.properties.OutputDirectoryProperty;
import com.sqlapp.data.db.command.properties.TargetFileProperty;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.YamlConverter;

import lombok.Getter;
import lombok.Setter;

/**
 * Applies declarative metadata rules to columns in a schema XML document.
 */
@Getter
@Setter
public class ColumnRuleTransformCommand extends AbstractCommand
		implements TargetFileProperty, OutputDirectoryProperty {

	private File targetFile;

	private File rulesFile;

	private File outputDirectory = new File("./");

	private File transformLogDirectory;

	private String transformLogFileName;

	@Override
	protected void doRun() {
		validateProperties();
		execute(() -> {
			DbCommonObject<?> root = SchemaUtils.readXml(targetFile);
			RuleSet ruleSet = new YamlConverter().fromJsonString(rulesFile, RuleSet.class);
			Map<String, Object> log = transform(root, ruleSet);
			ensureDirectory(outputDirectory, "output");
			File outputFile = new File(outputDirectory, targetFile.getName());
			if (targetFile.getCanonicalFile().equals(outputFile.getCanonicalFile())) {
				throw new CommandException("Input and output files must be different: " + outputFile);
			}
			root.writeXml(outputFile);
			info("Output column-rule schema XML: " + outputFile.getAbsolutePath());
			writeLog(log);
		});
	}

	private void validateProperties() {
		if (targetFile == null || !targetFile.isFile()) {
			throw new CommandException("targetFile does not exist or is not a file: " + targetFile);
		}
		if (rulesFile == null || !rulesFile.isFile()) {
			throw new CommandException("rulesFile does not exist or is not a file: " + rulesFile);
		}
		if (outputDirectory == null) {
			throw new CommandException("outputDirectory is required.");
		}
	}

	/**
	 * Applies rules to an already loaded schema model.
	 */
	public Map<String, Object> transform(DbCommonObject<?> root, RuleSet ruleSet) {
		if (ruleSet == null || ruleSet.getRules() == null) {
			throw new CommandException("rules are required.");
		}
		List<ColumnRule> rules = new ArrayList<>(ruleSet.getRules());
		validateRules(rules);
		rules.sort(Comparator.comparingInt(ColumnRule::getPriority).reversed());
		List<Map<String, Object>> results = new ArrayList<>();
		for (Table table : SchemaUtils.toTables(root)) {
			for (Column column : new ArrayList<>(table.getColumns())) {
				ColumnSnapshot source = new ColumnSnapshot(column);
				List<ColumnRule> matches = rules.stream().filter(rule -> matches(rule, table, source)).toList();
				if (matches.isEmpty()) {
					continue;
				}
				validateConflicts(table, source, matches);
				ColumnRule selected = matches.getFirst();
				Map<String, Object> result = mapOf("ruleId", selected.getId(), "mode", selected.getMode().name(),
						"source", columnLog(table, source), "matchedRuleIds",
						matches.stream().map(ColumnRule::getId).toList());
				Map<String, Object> target = targetLog(source, selected.getAction());
				result.put("target", target);
				if (selected.getMode() == RuleExecutionMode.APPLY) {
					if (!table.getRows().isEmpty()) {
						throw new CommandException("Row data conversion is not supported: table=" + table.getName()
								+ ", column=" + column.getName());
					}
					apply(column, selected.getAction());
					result.put("result", "applied");
				} else {
					result.put("result", "candidate");
				}
				results.add(result);
			}
		}
		return mapOf("formatVersion", 1, "source", mapOf("file",
				targetFile == null ? null : targetFile.getName()), "matches", results);
	}

	private void validateRules(List<ColumnRule> rules) {
		Set<String> ids = new java.util.HashSet<>();
		for (ColumnRule rule : rules) {
			if (rule.getId() == null || rule.getId().isBlank()) {
				throw new CommandException("Every rule requires a non-empty id.");
			}
			if (!ids.add(rule.getId())) {
				throw new CommandException("Duplicate rule id: " + rule.getId());
			}
			if (rule.getMatch() == null || rule.getAction() == null || rule.getAction().getDataType() == null) {
				throw new CommandException("Rule match and action.dataType are required: " + rule.getId());
			}
		}
	}

	private boolean matches(ColumnRule rule, Table table, ColumnSnapshot column) {
		ColumnMatch match = rule.getMatch();
		if (rule.getExcludeTables().contains(table.getName())
				|| rule.getExcludeColumns().contains(column.name)) {
			return false;
		}
		return matchesText(table.getCatalogName(), match.getCatalogName(), match.getCatalogNameRegex())
				&& matchesText(table.getSchemaName(), match.getSchemaName(), match.getSchemaNameRegex())
				&& matchesText(table.getName(), match.getTableName(), match.getTableNameRegex())
				&& matchesText(column.name, match.getColumnName(), match.getColumnNameRegex())
				&& (match.getDataTypes().isEmpty() || match.getDataTypes().contains(column.dataType))
				&& (match.getExactLength() == null || match.getExactLength().equals(column.length))
				&& (match.getMinimumLength() == null
						|| column.length != null && column.length >= match.getMinimumLength())
				&& (match.getMaximumLength() == null
						|| column.length != null && column.length <= match.getMaximumLength())
				&& (match.getNullable() == null || match.getNullable() == !column.notNull)
				&& (match.getPrimaryKey() == null || match.getPrimaryKey() == column.primaryKey)
				&& (match.getForeignKey() == null || match.getForeignKey() == column.foreignKey);
	}

	private boolean matchesText(String value, String exact, String regex) {
		if (exact != null && (value == null || !exact.equalsIgnoreCase(value))) {
			return false;
		}
		return regex == null || value != null && Pattern.compile(regex).matcher(value).matches();
	}

	private void validateConflicts(Table table, ColumnSnapshot column, List<ColumnRule> matches) {
		int priority = matches.getFirst().getPriority();
		List<ColumnRule> highest = matches.stream().filter(rule -> rule.getPriority() == priority).toList();
		if (highest.stream().map(rule -> rule.getAction().signature()).distinct().count() > 1) {
			throw new CommandException("Conflicting column rules have the same priority: table=" + table.getName()
					+ ", column=" + column.name + ", rules="
					+ highest.stream().map(ColumnRule::getId).toList());
		}
	}

	private void apply(Column column, ColumnAction action) {
		column.setDataType(action.getDataType());
		switch (action.getLengthHandling()) {
		case PRESERVE:
			break;
		case REMOVE:
			column.setLength((Number) null);
			break;
		case REPLACE:
			if (action.getLength() == null) {
				throw new CommandException("action.length is required for REPLACE.");
			}
			column.setLength(action.getLength());
			break;
		default:
			throw new CommandException("Unsupported lengthHandling: " + action.getLengthHandling());
		}
	}

	private Map<String, Object> columnLog(Table table, ColumnSnapshot column) {
		return mapOf("catalog", table.getCatalogName(), "schema", table.getSchemaName(), "table",
				table.getName(), "column", column.name, "dataType", column.dataType.name(), "length",
				column.length, "nullable", !column.notNull, "primaryKey", column.primaryKey, "foreignKey",
				column.foreignKey);
	}

	private Map<String, Object> targetLog(ColumnSnapshot source, ColumnAction action) {
		Long length = switch (action.getLengthHandling()) {
		case PRESERVE -> source.length;
		case REMOVE -> null;
		case REPLACE -> action.getLength();
		};
		return mapOf("dataType", action.getDataType().name(), "length", length, "lengthHandling",
				action.getLengthHandling().name());
	}

	private void writeLog(Map<String, Object> log) {
		File directory = transformLogDirectory != null ? transformLogDirectory : outputDirectory;
		ensureDirectory(directory, "transform log");
		String fileName = transformLogFileName;
		if (fileName == null || fileName.isBlank()) {
			String name = targetFile.getName();
			int dot = name.lastIndexOf('.');
			fileName = (dot > 0 ? name.substring(0, dot) : name) + "-column-transform.yaml";
		}
		File file = new File(directory, fileName);
		new YamlConverter().writeJsonValue(file, log);
		info("Output column-rule transform log: " + file.getAbsolutePath());
	}

	private void ensureDirectory(File directory, String type) {
		if (!directory.exists() && !directory.mkdirs()) {
			throw new CommandException("Failed to create " + type + " directory: " + directory);
		}
	}

	private Map<String, Object> mapOf(Object... values) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < values.length; i += 2) {
			map.put((String) values[i], values[i + 1]);
		}
		return map;
	}

	private static final class ColumnSnapshot {
		private final String name;
		private final DataType dataType;
		private final Long length;
		private final boolean notNull;
		private final boolean primaryKey;
		private final boolean foreignKey;

		private ColumnSnapshot(Column column) {
			this.name = column.getName();
			this.dataType = column.getDataType();
			this.length = column.getLength();
			this.notNull = column.isNotNull();
			this.primaryKey = column.isPrimaryKey();
			this.foreignKey = column.isForeignKey();
		}
	}

	@Getter
	@Setter
	public static class RuleSet {
		private int formatVersion = 1;
		private List<ColumnRule> rules = new ArrayList<>();
	}

	@Getter
	@Setter
	public static class ColumnRule {
		private String id;
		private String description;
		private int priority;
		private RuleExecutionMode mode = RuleExecutionMode.APPLY;
		private ColumnMatch match;
		private ColumnAction action;
		private List<String> excludeTables = new ArrayList<>();
		private List<String> excludeColumns = new ArrayList<>();
	}

	@Getter
	@Setter
	public static class ColumnMatch {
		private String catalogName;
		private String catalogNameRegex;
		private String schemaName;
		private String schemaNameRegex;
		private String tableName;
		private String tableNameRegex;
		private String columnName;
		private String columnNameRegex;
		private List<DataType> dataTypes = new ArrayList<>();
		private Long exactLength;
		private Long minimumLength;
		private Long maximumLength;
		private Boolean nullable;
		private Boolean primaryKey;
		private Boolean foreignKey;
	}

	@Getter
	@Setter
	public static class ColumnAction {
		private DataType dataType;
		private LengthHandling lengthHandling = LengthHandling.PRESERVE;
		private Long length;

		private String signature() {
			return dataType + ":" + lengthHandling + ":" + length;
		}
	}

	public enum LengthHandling {
		PRESERVE,
		REMOVE,
		REPLACE
	}

	public enum RuleExecutionMode {
		APPLY,
		REPORT_ONLY
	}
}
