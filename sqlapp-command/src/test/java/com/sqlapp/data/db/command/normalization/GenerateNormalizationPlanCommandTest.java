/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.YamlConverter;

class GenerateNormalizationPlanCommandTest {

	@TempDir
	File temporaryDirectory;

	@Test
	void testGenerateCandidatesAndPreviewSchema() throws Exception {
		Schema schema = new Schema("PUBLIC");
		Table orders = new Table("ORDERS");
		orders.getColumns().add(new Column("TENANT_CODE")
				.setDataType(DataType.CHAR).setLength(20).setNotNull(true));
		orders.getColumns().add(new Column("ORDER_NO")
				.setDataType(DataType.CHAR).setLength(20).setNotNull(true));
		orders.getColumns().add(new Column("ORDER_DATE")
				.setDataType(DataType.CHAR).setLength(8));
		orders.getColumns().add(new Column("ITEM_1")
				.setDataType(DataType.CHAR).setLength(30));
		orders.getColumns().add(new Column("ITEM_2")
				.setDataType(DataType.CHAR).setLength(30));
		orders.getColumns().add(new Column("QUANTITY_1")
				.setDataType(DataType.INT));
		orders.getColumns().add(new Column("QUANTITY_2")
				.setDataType(DataType.INT));
		orders.setPrimaryKey("PK_ORDERS", orders.getColumns().get("TENANT_CODE"),
				orders.getColumns().get("ORDER_NO"));
		schema.getTables().add(orders);
		Table noKey = new Table("LEGACY_MEMO");
		noKey.getColumns().add(new Column("MEMO")
				.setDataType(DataType.NCHAR).setLength(100));
		schema.getTables().add(noKey);
		File source = new File(temporaryDirectory, "legacy.xml");
		schema.writeXml(source);
		File output = new File(temporaryDirectory, "plan");

		GenerateNormalizationPlanCommand command =
				new GenerateNormalizationPlanCommand();
		command.setTargetFile(source);
		command.setOutputDirectory(output);
		command.setLocale(Locale.ENGLISH);
		command.run();

		File yaml = new File(output, "legacy-normalization-plan.yaml");
		File preview = new File(output, "legacy-normalization-preview.xml");
		assertTrue(yaml.isFile());
		assertTrue(preview.isFile());
		String text = Files.readString(yaml.toPath());
		assertTrue(text.contains("repeating-columns"));
		assertTrue(text.contains("composite-primary-key"));
		assertTrue(text.contains("date-like-character"));
		assertTrue(text.contains("char-to-varchar"));
		assertTrue(text.contains("missing-primary-key"));
		assertTrue(text.contains("nchar-to-nvarchar"));
		assertTrue(text.contains("proposed"));
		assertTrue(text.contains("Does the sequence number represent row order?"));
		Schema previewSchema = (Schema) SchemaUtils.readXml(preview);
		Table previewOrders = previewSchema.getTables().get("ORDERS");
		assertNotNull(previewOrders.getColumns().get("ID"));
		assertFalse(previewOrders.getColumns().contains("ITEM_1"));
		assertFalse(previewOrders.getColumns().contains("QUANTITY_1"));
		assertNotNull(previewSchema.getTables().get("ORDERS_DETAIL_1"));
		assertEquals(3, previewSchema.getTables().size());
	}

	@Test
	void testMessagesAreAvailableForSupportedLocales() {
		List<Locale> locales = List.of(Locale.ENGLISH, Locale.JAPANESE, Locale.GERMAN,
				Locale.FRENCH, Locale.SIMPLIFIED_CHINESE);
		for (Locale locale : locales) {
			assertFalse(GenerateNormalizationPlanCommand.getMessage(locale,
					"question.repeatingColumns.sequenceOrder").isBlank());
			assertFalse(GenerateNormalizationPlanCommand.getMessage(locale,
					"error.candidateThresholds").isBlank());
		}
		assertEquals("連番は行の順序を表しますか？",
				GenerateNormalizationPlanCommand.getMessage(Locale.JAPANESE,
						"question.repeatingColumns.sequenceOrder"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void testGenerateHierarchyBusinessKeys() throws Exception {
		Schema schema = new Schema("PUBLIC");
		Table tab = table("TAB", "A", "B", "C");
		schema.getTables().add(tab);
		Table tab1 = table("TAB1", "A", "B", "C", "D");
		schema.getTables().add(tab1);
		tab1.getConstraints().addForeignKeyConstraint("FK_TAB1_TAB",
				new Column[] { tab1.getColumns().get("A"), tab1.getColumns().get("B"),
						tab1.getColumns().get("C") },
				new Column[] { tab.getColumns().get("A"), tab.getColumns().get("B"), tab.getColumns().get("C") });
		Table tab11 = table("TAB1_1", "A", "B", "C", "D", "E");
		schema.getTables().add(tab11);
		tab11.getConstraints().addForeignKeyConstraint("FK_TAB1_1_TAB1",
				new Column[] { tab11.getColumns().get("A"), tab11.getColumns().get("B"),
						tab11.getColumns().get("C"), tab11.getColumns().get("D") },
				new Column[] { tab1.getColumns().get("A"), tab1.getColumns().get("B"),
						tab1.getColumns().get("C"), tab1.getColumns().get("D") });
		File source = new File(temporaryDirectory, "hierarchy.xml");
		schema.writeXml(source);
		File output = new File(temporaryDirectory, "hierarchy-plan");

		GenerateNormalizationPlanCommand command = new GenerateNormalizationPlanCommand();
		command.setTargetFile(source);
		command.setOutputDirectory(output);
		command.run();

		Map<String, Object> plan = new YamlConverter().fromJsonString(
				Files.readString(new File(output, "hierarchy-normalization-plan.yaml").toPath()), Map.class);
		List<Map<String, Object>> candidates = (List<Map<String, Object>>) plan.get("candidates");
		assertEquals(List.of("A", "B", "C"), businessKey(candidates, "TAB"));
		assertEquals(List.of("PARENT_ID", "D"), businessKey(candidates, "TAB1"));
		assertEquals(List.of("PARENT_ID", "E"), businessKey(candidates, "TAB1_1"));

		Schema preview = (Schema) SchemaUtils
				.readXml(new File(output, "hierarchy-normalization-preview.xml"));
		assertUnique(preview.getTables().get("TAB"), "A", "B", "C");
		assertUnique(preview.getTables().get("TAB1"), "PARENT_ID", "D");
		assertUnique(preview.getTables().get("TAB1_1"), "PARENT_ID", "E");
	}

	@SuppressWarnings("unchecked")
	private List<String> businessKey(List<Map<String, Object>> candidates, String table) {
		return candidates.stream()
				.filter(candidate -> "composite-primary-key".equals(candidate.get("category")))
				.filter(candidate -> table.equals(((Map<String, Object>) candidate.get("source")).get("table")))
				.map(candidate -> (Map<String, Object>) candidate.get("proposal"))
				.map(proposal -> (List<String>) proposal.get("businessKey")).findFirst().orElseThrow();
	}

	private Table table(String name, String... primaryKeyNames) {
		Table table = new Table(name);
		for (String columnName : primaryKeyNames) {
			table.getColumns().add(new Column(columnName).setDataType(DataType.VARCHAR).setLength(32).setNotNull(true));
		}
		table.setPrimaryKey("PK_" + name,
				java.util.Arrays.stream(primaryKeyNames).map(columnName -> table.getColumns().get(columnName))
						.toArray(Column[]::new));
		return table;
	}

	private void assertUnique(Table table, String... names) {
		assertTrue(table.getConstraints().getUniqueConstraints(constraint -> !constraint.isPrimaryKey()).stream()
				.anyMatch(constraint -> constraint.getColumns().toColumns().stream().map(Column::getName).toList()
						.equals(List.of(names))));
	}
}
