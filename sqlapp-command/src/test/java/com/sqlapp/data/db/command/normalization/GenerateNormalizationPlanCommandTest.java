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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;

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
}
