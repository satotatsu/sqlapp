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

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.loader.SchemaFileLoaderResolver;

import io.github.spannm.jackcess.ColumnBuilder;
import io.github.spannm.jackcess.Database;
import io.github.spannm.jackcess.DatabaseBuilder;
import io.github.spannm.jackcess.IndexBuilder;
import io.github.spannm.jackcess.RelationshipBuilder;
import io.github.spannm.jackcess.PropertyMap;
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
					.addColumn(new ColumnBuilder("国コード",
							io.github.spannm.jackcess.DataType.TEXT)
							.withLengthInUnits(2))
					.addIndex(new IndexBuilder("PK_地域マスタ")
							.withColumns("地域ID", "国コード")
							.withPrimaryKey())
					.toTable(database);
			final io.github.spannm.jackcess.Table source = new TableBuilder(
					"顧客マスタ")
					.addColumn(new ColumnBuilder("顧客ID",
							io.github.spannm.jackcess.DataType.LONG)
							.withAutoNumber(true))
					.addColumn(new ColumnBuilder("顧客名",
							io.github.spannm.jackcess.DataType.TEXT)
							.withLengthInUnits(100)
							.withProperty(PropertyMap.REQUIRED_PROP, true)
							.withProperty(PropertyMap.DEFAULT_VALUE_PROP, "未設定")
							.withProperty(PropertyMap.DESCRIPTION_PROP, "顧客の表示名")
							.withProperty(PropertyMap.VALIDATION_RULE_PROP,
									"<> ''"))
					.addColumn(new ColumnBuilder("備考",
							io.github.spannm.jackcess.DataType.MEMO))
					.addColumn(new ColumnBuilder("地域ID",
							io.github.spannm.jackcess.DataType.LONG))
					.addColumn(new ColumnBuilder("国コード",
							io.github.spannm.jackcess.DataType.TEXT)
							.withLengthInUnits(2))
					.addColumn(new ColumnBuilder("画像",
							io.github.spannm.jackcess.DataType.OLE))
					.addIndex(new IndexBuilder("PK_顧客マスタ")
							.withColumns("顧客ID").withPrimaryKey())
					.addIndex(new IndexBuilder("IDX_顧客名")
							.withColumns("顧客名"))
					.toTable(database);
			source.getProperties().put(PropertyMap.DESCRIPTION_PROP,
					"顧客情報を保持するテーブル");
			source.getProperties().save();
			new RelationshipBuilder(parent, source)
					.addColumns("地域ID", "地域ID")
					.addColumns("国コード", "国コード")
					.withReferentialIntegrity()
					.withCascadeUpdates().withName("FK_顧客_地域")
					.toRelationship(database);
			parent.addRow(1, "JP");
			source.addRow(io.github.spannm.jackcess.Column.AUTO_NUMBER,
					"山田 太郎", "東京都", 1, "JP", new byte[] { 1, 2, 3 });
			source.addRow(io.github.spannm.jackcess.Column.AUTO_NUMBER,
					"佐藤 花子", null, 1, "JP", null);
		}

		final var schema = MdbFileLoader.load(file);
		final var table = schema.getTables().get("顧客マスタ");
		assertNotNull(table);
		assertEquals("顧客情報を保持するテーブル", table.getRemarks());
		assertEquals(DataType.INT,
				table.getColumns().get("顧客ID").getDataType());
		assertTrue(table.getColumns().get("顧客ID").isIdentity());
		assertEquals(100L, table.getColumns().get("顧客名").getLength());
		assertTrue(table.getColumns().get("顧客名").isNotNull());
		assertEquals("未設定",
				table.getColumns().get("顧客名").getDefaultValue());
		assertEquals("顧客の表示名",
				table.getColumns().get("顧客名").getRemarks());
		assertEquals("<> ''", table.getColumns().get("顧客名")
				.getCheckConstraint().getExpression());
		assertNotNull(table.getConstraints().getPrimaryKeyConstraint());
		assertNotNull(table.getIndexes().get("IDX_顧客名"));
		final var foreignKey = (ForeignKeyConstraint) table.getConstraints()
				.get("FK_顧客_地域");
		assertNotNull(foreignKey);
		assertEquals(2, foreignKey.getColumns().size());
		assertEquals(2, foreignKey.getRelatedColumns().size());
		assertEquals(2, schema.getTables().get("地域マスタ")
				.getConstraints().getPrimaryKeyConstraint().getColumns().size());

		final var rows = table.getRows().iterator();
		assertTrue(rows.hasNext());
		final var first = rows.next();
		assertEquals(1, ((Number) first.get("顧客ID")).intValue());
		assertEquals("山田 太郎", first.get("顧客名"));
		assertEquals("東京都", first.get("備考"));
		assertTrue(first.get("画像") instanceof byte[]);
		assertEquals(3, ((byte[]) first.get("画像")).length);
		assertTrue(rows.hasNext());
		final var second = rows.next();
		assertEquals("佐藤 花子", second.get("顧客名"));
		assertFalse(rows.hasNext());

		final var singleTable = MdbFileLoader.loadTable(file, "顧客マスタ");
		assertEquals("顧客マスタ", singleTable.getName());
		final var singleRows = singleTable.getRows().iterator();
		assertTrue(singleRows.hasNext());
		assertEquals("山田 太郎", singleRows.next().get("顧客名"));
		assertTrue(singleRows instanceof AutoCloseable);
		((AutoCloseable) singleRows).close();
		final Path moved = tempDirectory.resolve("移動後.accdb");
		Files.move(file, moved);
		assertTrue(Files.exists(moved));
	}

	@Test
	void loadsLegacyMdbWithJapaneseIdentifiers() throws Exception {
		final Path file = tempDirectory.resolve("旧形式.mdb");
		try (Database database = DatabaseBuilder.create(
				Database.FileFormat.V2000, file.toFile())) {
			final io.github.spannm.jackcess.Table source = new TableBuilder(
					"受注明細")
					.addColumn(new ColumnBuilder("明細番号",
							io.github.spannm.jackcess.DataType.LONG))
					.addColumn(new ColumnBuilder("商品名",
							io.github.spannm.jackcess.DataType.TEXT)
							.withLengthInUnits(50))
					.addIndex(new IndexBuilder("PK_受注明細")
							.withColumns("明細番号").withPrimaryKey())
					.toTable(database);
			source.addRow(1, "鉛筆");
		}

		final var table = MdbFileLoader.loadTable(file, "受注明細");
		assertEquals("受注明細", table.getName());
		assertNotNull(table.getConstraints().getPrimaryKeyConstraint());
		final var rows = table.getRows().iterator();
		assertTrue(rows.hasNext());
		assertEquals("鉛筆", rows.next().get("商品名"));
		assertFalse(rows.hasNext());
		final var provider = SchemaFileLoaderResolver.resolve(file);
		assertTrue(provider instanceof MdbSchemaFileLoader);
		assertNotNull(provider.loadSchema(file).getTables().get("受注明細"));
	}
}
