/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.normalization.ColumnRuleTransformCommand.ColumnAction;
import com.sqlapp.data.db.command.normalization.ColumnRuleTransformCommand.ColumnMatch;
import com.sqlapp.data.db.command.normalization.ColumnRuleTransformCommand.ColumnRule;
import com.sqlapp.data.db.command.normalization.ColumnRuleTransformCommand.LengthHandling;
import com.sqlapp.data.db.command.normalization.ColumnRuleTransformCommand.RuleExecutionMode;
import com.sqlapp.data.db.command.normalization.ColumnRuleTransformCommand.RuleSet;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.YamlConverter;

class ColumnRuleTransformCommandTest {

	@TempDir
	File temporaryDirectory;

	@Test
	void testApplyRulesFromYaml() throws XMLStreamException, IOException {
		Schema schema = createSchema();
		File input = new File(temporaryDirectory, "input/schema.xml");
		assertTrue(input.getParentFile().mkdirs());
		schema.writeXml(input);
		File rules = new File(temporaryDirectory, "rules.yaml");
		Files.writeString(rules.toPath(), """
				formatVersion: 1
				rules:
				  - id: date8
				    priority: 100
				    match:
				      columnNameRegex: "(?i).*_(YYYYMMDD|YMD)$"
				      dataTypes: [CHAR, VARCHAR]
				      exactLength: 8
				    action:
				      dataType: DATE
				      lengthHandling: REMOVE
				  - id: long-char
				    priority: 10
				    match:
				      dataTypes: [CHAR]
				      minimumLength: 100
				    action:
				      dataType: VARCHAR
				      lengthHandling: PRESERVE
				  - id: long-nchar
				    priority: 10
				    match:
				      dataTypes: [NCHAR]
				      minimumLength: 100
				    action:
				      dataType: NVARCHAR
				      lengthHandling: PRESERVE
				""");
		File output = new File(temporaryDirectory, "output");
		File logs = new File(temporaryDirectory, "logs");
		ColumnRuleTransformCommand command = new ColumnRuleTransformCommand();
		command.setTargetFile(input);
		command.setRulesFile(rules);
		command.setOutputDirectory(output);
		command.setTransformLogDirectory(logs);
		command.run();

		Schema converted = (Schema) SchemaUtils.readXml(new File(output, "schema.xml"));
		Table table = converted.getTables().get("ORDERS");
		assertColumn(table, "ORDER_YYYYMMDD", DataType.DATE, null);
		assertColumn(table, "DESCRIPTION", DataType.VARCHAR, 200L);
		assertColumn(table, "N_DESCRIPTION", DataType.NVARCHAR, 200L);
		assertColumn(table, "CODE", DataType.CHAR, 8L);
		File logFile = new File(logs, "schema-column-transform.yaml");
		assertTrue(logFile.isFile());
		@SuppressWarnings("unchecked")
		Map<String, Object> log = new YamlConverter().fromJsonString(logFile, Map.class);
		assertEquals(3, ((List<?>) log.get("matches")).size());
	}

	@Test
	void testReportOnlyAndExclude() {
		Schema schema = createSchema();
		RuleSet set = new RuleSet();
		ColumnRule report = rule("report", 10, DataType.CHAR, 8L, DataType.VARCHAR);
		report.setMode(RuleExecutionMode.REPORT_ONLY);
		report.setExcludeColumns(List.of("CODE"));
		report.getMatch().setColumnName("ORDER_YYYYMMDD");
		set.setRules(List.of(report));

		Map<String, Object> log = new ColumnRuleTransformCommand().transform(schema, set);

		assertEquals(DataType.CHAR, schema.getTables().get("ORDERS").getColumns().get("ORDER_YYYYMMDD").getDataType());
		assertEquals(1, ((List<?>) log.get("matches")).size());
	}

	@Test
	void testRejectSamePriorityConflict() {
		Schema schema = createSchema();
		RuleSet set = new RuleSet();
		ColumnRule first = rule("first", 10, DataType.CHAR, 8L, DataType.VARCHAR);
		ColumnRule second = rule("second", 10, DataType.CHAR, 8L, DataType.DATE);
		set.setRules(List.of(first, second));

		assertThrows(CommandException.class, () -> new ColumnRuleTransformCommand().transform(schema, set));
	}

	private Schema createSchema() {
		Schema schema = new Schema("PUBLIC");
		Table table = new Table("ORDERS");
		table.getColumns().add(new Column("ORDER_YYYYMMDD").setDataType(DataType.CHAR).setLength(8));
		table.getColumns().add(new Column("DESCRIPTION").setDataType(DataType.CHAR).setLength(200));
		table.getColumns().add(new Column("N_DESCRIPTION").setDataType(DataType.NCHAR).setLength(200));
		table.getColumns().add(new Column("CODE").setDataType(DataType.CHAR).setLength(8));
		schema.getTables().add(table);
		return schema;
	}

	private ColumnRule rule(String id, int priority, DataType sourceType, Long length, DataType targetType) {
		ColumnMatch match = new ColumnMatch();
		match.setDataTypes(List.of(sourceType));
		match.setExactLength(length);
		ColumnAction action = new ColumnAction();
		action.setDataType(targetType);
		action.setLengthHandling(LengthHandling.PRESERVE);
		ColumnRule rule = new ColumnRule();
		rule.setId(id);
		rule.setPriority(priority);
		rule.setMatch(match);
		rule.setAction(action);
		return rule;
	}

	private void assertColumn(Table table, String name, DataType dataType, Long length) {
		Column column = table.getColumns().get(name);
		assertNotNull(column);
		assertEquals(dataType, column.getDataType());
		assertEquals(length, column.getLength());
	}
}
