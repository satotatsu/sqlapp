/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

import io.github.spannm.jackcess.ColumnBuilder;
import io.github.spannm.jackcess.Database;
import io.github.spannm.jackcess.DatabaseBuilder;
import io.github.spannm.jackcess.IndexBuilder;
import io.github.spannm.jackcess.TableBuilder;

class MdbJackcessSupportTest {
	@TempDir
	Path tempDirectory;

	@Test
	void bulkCrudReturnsEveryAutoNumberAndUsesPrimaryKey() throws Exception {
		final Path file = tempDirectory.resolve("bulk.accdb");
		try (Database database = DatabaseBuilder.create(Database.FileFormat.V2010,
				file.toFile())) {
			new TableBuilder("商品 マスタ")
					.addColumn(new ColumnBuilder("商品ID",
							io.github.spannm.jackcess.DataType.LONG)
							.withAutoNumber(true))
					.addColumn(new ColumnBuilder("商品名",
							io.github.spannm.jackcess.DataType.TEXT)
							.withLengthInUnits(100))
					.addColumn(new ColumnBuilder("分類",
							io.github.spannm.jackcess.DataType.TEXT)
							.withLengthInUnits(20))
					.addIndex(new IndexBuilder("PK_商品").withColumns("商品ID")
							.withPrimaryKey())
					.toTable(database);
		}

		final List<Map<String, Object>> rows = new ArrayList<>();
		for (int i = 0; i < 250; i++) {
			rows.add(new LinkedHashMap<>(Map.of("商品名", "商品" + i,
					"分類", "A")));
		}
		try (MdbJackcessSupport writer = MdbJackcessSupport.open(file)) {
			assertThrows(IllegalStateException.class,
					() -> MdbJackcessSupport.open(file));
			assertEquals(250, writer.insertRows("商品 マスタ", rows).size());
			assertEquals(1, ((Number) rows.get(0).get("商品ID")).intValue());
			assertEquals(250,
					((Number) rows.get(249).get("商品ID")).intValue());
			assertEquals(2, writer.updateRowsByPrimaryKey("商品 マスタ",
					List.of(new LinkedHashMap<>(Map.of("商品ID", 1,
							"商品名", "更新1", "分類", "B")),
							new LinkedHashMap<>(Map.of("商品ID", 250,
									"商品名", "更新250", "分類", "B")))));
			assertEquals(2, writer.deleteRowsByPrimaryKey("商品 マスタ",
					List.of(Map.of("商品ID", 2), Map.of("商品ID", 249))));
		}

		final Table loaded = MdbFileLoader.loadTable(file, "商品 マスタ");
		int count = 0;
		String firstName = null;
		for (final var loadedRow : loaded.getRows()) {
			if (((Number) loadedRow.get("商品ID")).intValue() == 1) {
				firstName = loadedRow.get("商品名");
			}
			count++;
		}
		assertEquals(248, count);
		assertEquals("更新1", firstName);
	}

	@Test
	void upsertUpdatesByKeyAndBulkInsertsMissingRows() throws Exception {
		final Path file = tempDirectory.resolve("upsert.accdb");
		try (Database database = DatabaseBuilder.create(Database.FileFormat.V2010,
				file.toFile())) {
			final io.github.spannm.jackcess.Table table = new TableBuilder("対象")
					.addColumn(new ColumnBuilder("ID",
							io.github.spannm.jackcess.DataType.LONG)
							.withAutoNumber(true))
					.addColumn(new ColumnBuilder("値",
							io.github.spannm.jackcess.DataType.TEXT))
					.addIndex(new IndexBuilder("PK_対象").withColumns("ID")
							.withPrimaryKey())
					.toTable(database);
			table.addRow(io.github.spannm.jackcess.Column.AUTO_NUMBER, "旧値");
		}
		final Map<String, Object> generated = new LinkedHashMap<>();
		generated.put("値", "自動採番");
		try (MdbJackcessSupport writer = MdbJackcessSupport.open(file)) {
			final var result = writer.upsertRowsByPrimaryKey("対象", List.of(
					new LinkedHashMap<>(Map.of("ID", 1, "値", "更新値")),
					new LinkedHashMap<>(Map.of("ID", 100, "値", "明示キー")),
					generated));
			assertEquals(1, result.updated());
			assertEquals(2, result.inserted());
			assertEquals(3, result.total());
			assertNotNull(generated.get("ID"));
		}
		int count = 0;
		for (final var ignored : MdbFileLoader.loadTable(file, "対象").getRows()) {
			count++;
		}
		assertEquals(3, count);
	}

	@Test
	void addsColumnIndexAndRelationship() throws Exception {
		final Path file = tempDirectory.resolve("schema.accdb");
		try (Database database = DatabaseBuilder.create(Database.FileFormat.V2010,
				file.toFile())) {
			new TableBuilder("親")
					.addColumn(new ColumnBuilder("ID",
							io.github.spannm.jackcess.DataType.LONG))
					.addIndex(new IndexBuilder("PK_親").withColumns("ID")
							.withPrimaryKey())
					.toTable(database);
			new TableBuilder("子")
					.addColumn(new ColumnBuilder("ID",
							io.github.spannm.jackcess.DataType.LONG))
					.addColumn(new ColumnBuilder("親ID",
							io.github.spannm.jackcess.DataType.LONG))
					.addIndex(new IndexBuilder("PK_子").withColumns("ID")
							.withPrimaryKey())
					.toTable(database);
		}

		final Table parent = new Table("親");
		final Column parentId = new Column("ID").setDataType(DataType.INT);
		parent.getColumns().add(parentId);
		parent.setPrimaryKey("PK_親", parentId);
		final Table child = new Table("子");
		final Column childId = new Column("ID").setDataType(DataType.INT);
		final Column parentReference = new Column("親ID")
				.setDataType(DataType.INT);
		child.getColumns().add(childId);
		child.getColumns().add(parentReference);
		child.setPrimaryKey("PK_子", childId);
		final var foreignKey = child.getConstraints().addForeignKeyConstraint(
				"FK_子_親", parentReference, parentId)
				.setUpdateRule(CascadeRule.Cascade)
				.setDeleteRule(CascadeRule.Cascade);
		final Column note = new Column("備考欄").setDataType(DataType.NVARCHAR)
				.setLength(80L);
		final Index index = new Index("IDX_子_親", parentReference);

		try (MdbJackcessSupport writer = MdbJackcessSupport.open(file)) {
			writer.addColumn("子", note);
			writer.addIndex("子", index);
			writer.addRelationship(foreignKey);
		}

		final Schema loaded = MdbFileLoader.load(file);
		assertNotNull(loaded.getTables().get("子").getColumns().get("備考欄"));
		assertNotNull(loaded.getTables().get("子").getIndexes()
				.get("IDX_子_親"));
		final var loadedForeignKey = loaded.getTables().get("子")
				.getConstraints().get("FK_子_親");
		assertNotNull(loadedForeignKey);
	}

	@Test
	void rebuildsToNewFileWithRenameTypeAndDroppedColumn() throws Exception {
		final Path source = tempDirectory.resolve("source.accdb");
		final Path target = tempDirectory.resolve("rebuilt.accdb");
		try (Database database = DatabaseBuilder.create(Database.FileFormat.V2019,
				source.toFile())) {
			final io.github.spannm.jackcess.Table table = new TableBuilder("顧客")
					.addColumn(new ColumnBuilder("ID",
							io.github.spannm.jackcess.DataType.LONG))
					.addColumn(new ColumnBuilder("旧名称",
							io.github.spannm.jackcess.DataType.TEXT)
							.withLengthInUnits(20))
					.addColumn(new ColumnBuilder("削除対象",
							io.github.spannm.jackcess.DataType.TEXT))
					.addIndex(new IndexBuilder("PK_顧客").withColumns("ID")
							.withPrimaryKey())
					.toTable(database);
			table.addRow(1, "山田", "不要");
		}

		final Schema schema = new Schema("");
		final Table customer = new Table("顧客");
		final Column id = new Column("ID").setDataType(DataType.BIGINT)
				.setNotNull(true);
		final Column name = new Column("新名称").setDataType(DataType.NVARCHAR)
				.setLength(100L);
		customer.getColumns().add(id);
		customer.getColumns().add(name);
		customer.setPrimaryKey("PK_顧客", id);
		schema.getTables().add(customer);

		MdbJackcessSupport.rebuild(source, target, schema,
				Map.of("顧客", Map.of("新名称", "旧名称")));
		assertTrue(java.nio.file.Files.exists(source));
		final Table loaded = MdbFileLoader.loadTable(target, "顧客");
		assertEquals(DataType.BIGINT, loaded.getColumns().get("ID").getDataType());
		assertNotNull(loaded.getColumns().get("新名称"));
		assertFalse(loaded.getColumns().contains("削除対象"));
		final var rebuiltRows = loaded.getRows().iterator();
		assertTrue(rebuiltRows.hasNext());
		assertEquals("山田", rebuiltRows.next().get("新名称"));
		assertFalse(rebuiltRows.hasNext());
		assertThrows(IllegalArgumentException.class,
				() -> MdbJackcessSupport.rebuild(source, source, schema));
	}

	@Test
	void rebuildsWithoutSecondaryIndexAndKeepsSource() throws Exception {
		final Path source = tempDirectory.resolve("indexed.accdb");
		final Path target = tempDirectory.resolve("without-index.accdb");
		try (Database database = DatabaseBuilder.create(Database.FileFormat.V2010,
				source.toFile())) {
			final io.github.spannm.jackcess.Table table = new TableBuilder("対象")
					.addColumn(new ColumnBuilder("ID",
							io.github.spannm.jackcess.DataType.LONG))
					.addColumn(new ColumnBuilder("検索値",
							io.github.spannm.jackcess.DataType.TEXT))
					.addIndex(new IndexBuilder("PK_対象").withColumns("ID")
							.withPrimaryKey())
					.addIndex(new IndexBuilder("IDX_検索値")
							.withColumns("検索値"))
					.toTable(database);
			table.addRow(1, "保持データ");
		}

		MdbJackcessSupport.rebuildWithoutIndex(source, target, "対象",
				"IDX_検索値");
		assertNotNull(MdbFileLoader.loadTable(source, "対象").getIndexes()
				.get("IDX_検索値"));
		final Table rebuilt = MdbFileLoader.loadTable(target, "対象");
		assertFalse(rebuilt.getIndexes().contains("IDX_検索値"));
		assertNotNull(rebuilt.getPrimaryKeyConstraint());
		final var rows = rebuilt.getRows().iterator();
		assertTrue(rows.hasNext());
		assertEquals("保持データ", rows.next().get("検索値"));
		assertThrows(IllegalArgumentException.class,
				() -> MdbJackcessSupport.rebuildWithoutIndex(source,
						tempDirectory.resolve("invalid.accdb"), "対象",
						"PK_対象"));
	}

	@Test
	void createsReadsAndDropsSavedSelectAndUnionQueries() throws Exception {
		final Path file = tempDirectory.resolve("queries.accdb");
		try (Database database = DatabaseBuilder.create(Database.FileFormat.V2010,
				file.toFile())) {
			new TableBuilder("販売")
					.addColumn(new ColumnBuilder("ID",
							io.github.spannm.jackcess.DataType.LONG))
					.addColumn(new ColumnBuilder("金額",
							io.github.spannm.jackcess.DataType.LONG))
					.toTable(database);
		}
		try (MdbJackcessSupport writer = MdbJackcessSupport.open(file)) {
			writer.createSavedQuery("高額販売",
					"SELECT ID, 金額 FROM [販売] WHERE 金額 >= 100 ORDER BY ID DESC");
			writer.createSavedQuery("販売統合",
					"SELECT ID FROM [販売] UNION ALL SELECT ID FROM [販売]");
			assertThrows(IllegalArgumentException.class,
					() -> writer.createSavedQuery("高額販売",
							"SELECT * FROM [販売]"));
		}
		try (Database database = new DatabaseBuilder().withPath(file)
				.withReadOnly(true).open()) {
			final var queries = database.getQueries();
			assertEquals(2, queries.size());
			assertTrue(queries.stream().anyMatch(query -> "高額販売"
					.equals(query.getName()) && query.toSQLString()
							.contains("WHERE 金額 >= 100")));
			assertTrue(queries.stream().anyMatch(query -> "販売統合"
					.equals(query.getName()) && query.toSQLString()
							.contains("UNION ALL")));
		}
		try (MdbJackcessSupport writer = MdbJackcessSupport.open(file)) {
			assertTrue(writer.dropSavedQuery("高額販売"));
			assertFalse(writer.dropSavedQuery("存在しない"));
		}
		try (Database database = new DatabaseBuilder().withPath(file)
				.withReadOnly(true).open()) {
			assertEquals(List.of("販売統合"), database.getQueries().stream()
					.map(query -> query.getName()).toList());
		}
	}

	@Test
	void rebuildPreservesSupportedSavedQueries() throws Exception {
		final Path source = tempDirectory.resolve("query-source.accdb");
		final Path target = tempDirectory.resolve("query-target.accdb");
		try (Database database = DatabaseBuilder.create(Database.FileFormat.V2010,
				source.toFile())) {
			new TableBuilder("明細")
					.addColumn(new ColumnBuilder("ID",
							io.github.spannm.jackcess.DataType.LONG))
					.toTable(database);
		}
		try (MdbJackcessSupport writer = MdbJackcessSupport.open(source)) {
			writer.createSavedQuery("明細一覧", "SELECT * FROM [明細]");
		}
		final Schema schema = MdbFileLoader.loadSchema(source);
		MdbJackcessSupport.rebuild(source, target, schema);
		try (Database database = new DatabaseBuilder().withPath(target)
				.withReadOnly(true).open()) {
			assertEquals("明細一覧", database.getQueries().get(0).getName());
			assertTrue(database.getQueries().get(0).toSQLString()
					.contains("SELECT *"));
		}
	}
}
