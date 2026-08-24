/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;

import io.github.spannm.jackcess.ColumnBuilder;
import io.github.spannm.jackcess.Database;
import io.github.spannm.jackcess.DatabaseBuilder;
import io.github.spannm.jackcess.IndexBuilder;
import io.github.spannm.jackcess.RelationshipBuilder;
import io.github.spannm.jackcess.TableBuilder;

class MdbFileLoaderTest {

	@TempDir
	Path tempDirectory;

	@Test
	void loadsJapaneseIdentifiersAndRowsWithoutJdbc() throws Exception {
		final Path file = tempDirectory.resolve("日本語データ.accdb");
		try (Database database = DatabaseBuilder.create(
				Database.FileFormat.V2010, file.toFile())) {
			final io.github.spannm.jackcess.Table parent = new TableBuilder(
					"地域マスタ")
					.addColumn(new ColumnBuilder("地域ID",
							io.github.spannm.jackcess.DataType.LONG))
					.addIndex(new IndexBuilder("PK_地域マスタ")
							.withColumns("地域ID").withPrimaryKey())
					.toTable(database);
			final io.github.spannm.jackcess.Table source = new TableBuilder(
					"顧客マスタ")
					.addColumn(new ColumnBuilder("顧客ID",
							io.github.spannm.jackcess.DataType.LONG)
							.withAutoNumber(true))
					.addColumn(new ColumnBuilder("顧客名",
							io.github.spannm.jackcess.DataType.TEXT)
							.withLengthInUnits(100))
					.addColumn(new ColumnBuilder("備考",
							io.github.spannm.jackcess.DataType.MEMO))
					.addColumn(new ColumnBuilder("地域ID",
							io.github.spannm.jackcess.DataType.LONG))
					.addIndex(new IndexBuilder("PK_顧客マスタ")
							.withColumns("顧客ID").withPrimaryKey())
					.addIndex(new IndexBuilder("IDX_顧客名")
							.withColumns("顧客名"))
					.toTable(database);
			new RelationshipBuilder(parent, source)
					.addColumns("地域ID", "地域ID").withReferentialIntegrity()
					.withCascadeUpdates().withName("FK_顧客_地域")
					.toRelationship(database);
			parent.addRow(1);
			source.addRow(io.github.spannm.jackcess.Column.AUTO_NUMBER,
					"山田 太郎", "東京都", 1);
			source.addRow(io.github.spannm.jackcess.Column.AUTO_NUMBER,
					"佐藤 花子", null, 1);
		}

		final var schema = MdbFileLoader.load(file);
		final var table = schema.getTables().get("顧客マスタ");
		assertNotNull(table);
		assertEquals(DataType.INT,
				table.getColumns().get("顧客ID").getDataType());
		assertTrue(table.getColumns().get("顧客ID").isIdentity());
		assertEquals(100L, table.getColumns().get("顧客名").getLength());
		assertNotNull(table.getConstraints().getPrimaryKeyConstraint());
		assertNotNull(table.getIndexes().get("IDX_顧客名"));
		final var foreignKey = table.getConstraints().get("FK_顧客_地域");
		assertNotNull(foreignKey);

		final var rows = table.getRows().iterator();
		assertTrue(rows.hasNext());
		final var first = rows.next();
		assertEquals(1, ((Number) first.get("顧客ID")).intValue());
		assertEquals("山田 太郎", first.get("顧客名"));
		assertEquals("東京都", first.get("備考"));
		assertTrue(rows.hasNext());
		final var second = rows.next();
		assertEquals("佐藤 花子", second.get("顧客名"));
		assertFalse(rows.hasNext());

		final var singleTable = MdbFileLoader.loadTable(file, "顧客マスタ");
		assertEquals("顧客マスタ", singleTable.getName());
		final var singleRows = singleTable.getRows().iterator();
		assertTrue(singleRows.hasNext());
		assertEquals("山田 太郎", singleRows.next().get("顧客名"));
	}
}
