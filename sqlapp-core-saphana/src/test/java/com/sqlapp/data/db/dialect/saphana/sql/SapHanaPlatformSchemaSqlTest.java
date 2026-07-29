/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-saphana.
 */
package com.sqlapp.data.db.dialect.saphana.sql;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Table;

class SapHanaPlatformSchemaSqlTest extends AbstractSapHanaSqlFactoryTest {

	@Test
	void testCloudTypesAreNotResolvedOnPlatform() {
		final Column json = new Column();
		json.setDialect(dialect);
		json.setDataTypeName("JSON");
		assertEquals(DataType.OTHER, json.getDataType());
		final Column vector = new Column();
		vector.setDialect(dialect);
		vector.setDataTypeName("REAL_VECTOR(128)");
		assertEquals(DataType.OTHER, vector.getDataType());
	}

	@Test
	void testCreateFullTextIndex() {
		final Table table = new Table("DOCUMENTS");
		table.setDialect(dialect);
		final Column content = new Column("CONTENT")
				.setDataType(DataType.NCLOB);
		final Column language = new Column("LANGUAGE_CODE")
				.setDataType(DataType.NVARCHAR).setLength(5);
		table.getColumns().add(content);
		table.getColumns().add(language);
		final Index index = new Index("IDX_DOCUMENTS_TEXT", content)
				.setIndexType(IndexType.FullText);
		index.getSpecifics().put(
				SapHanaCreateIndexFactory.LANGUAGE_COLUMN,
				language.getName());
		index.getSpecifics().put(
				SapHanaCreateIndexFactory.FAST_PREPROCESS, true);
		index.getSpecifics().put(
				SapHanaCreateIndexFactory.FUZZY_SEARCH_INDEX, false);
		index.getSpecifics().put(
				SapHanaCreateIndexFactory.FLUSH_EVERY_MINUTES, 5);
		index.getSpecifics().put(
				SapHanaCreateIndexFactory.FLUSH_AFTER_DOCUMENTS, 1000);
		index.getSpecifics().put(
				SapHanaCreateIndexFactory.CONFIGURATION,
				"EXTRACTION_CORE");
		table.getIndexes().add(index);

		final String sql = sqlFactoryRegistry.createSql(index, SqlType.CREATE)
				.get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.contains(
				"CREATE FULLTEXT INDEX IDX_DOCUMENTS_TEXT ON DOCUMENTS ( CONTENT)"),
				sql);
		assertTrue(sql.contains("LANGUAGE COLUMN LANGUAGE_CODE"), sql);
		assertTrue(sql.contains("FAST PREPROCESS ON"), sql);
		assertTrue(sql.contains("FUZZY SEARCH INDEX OFF"), sql);
		assertTrue(sql.contains(
				"ASYNC FLUSH EVERY 5 MINUTES OR AFTER 1000 DOCUMENTS"),
				sql);
		assertTrue(sql.contains("CONFIGURATION 'EXTRACTION_CORE'"), sql);
	}
}
