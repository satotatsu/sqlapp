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

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.loader.SchemaFileLoaderResolver;

import io.github.spannm.jackcess.ColumnBuilder;
import io.github.spannm.jackcess.Database;
import io.github.spannm.jackcess.DatabaseBuilder;
import io.github.spannm.jackcess.IndexBuilder;
import io.github.spannm.jackcess.RelationshipBuilder;
import io.github.spannm.jackcess.PropertyMap;
import io.github.spannm.jackcess.TableBuilder;
import io.github.spannm.jackcess.query.Query;

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
					.addIndex(new IndexBuilder("IDX_顧客名_地域")
							.withColumns("顧客名")
							.withColumns(false, "地域ID"))
					.toTable(database);
			source.getProperties().put(PropertyMap.DESCRIPTION_PROP,
					"顧客情報を保持するテーブル");
			source.getProperties().put(PropertyMap.VALIDATION_RULE_PROP,
					"[地域ID] > 0");
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
		assertTrue(table.getConstraints().stream()
				.anyMatch(c -> c instanceof CheckConstraint
						&& "[地域ID] > 0".equals(
								((CheckConstraint) c)
										.getExpression())));
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
		final var orderedIndex = table.getIndexes().get("IDX_顧客名_地域");
		assertEquals(Order.Asc, orderedIndex.getColumns().get(0).getOrder());
		assertEquals(Order.Desc, orderedIndex.getColumns().get(1).getOrder());
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
		assertNotNull(singleTable.getConstraints().get("FK_顧客_地域"));
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
		final Path file = tempDirectory.resolve("旧形式.MDB");
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
		assertNotNull(SchemaFileLoaderResolver.loadSchema(file).getTables()
				.get("受注明細"));
		assertEquals("受注明細", SchemaFileLoaderResolver
				.loadTable(file, "受注明細").getName());
		assertNotNull(SchemaFileLoaderResolver.loadSchema(file.toFile())
				.getTables().get("受注明細"));
		assertEquals("受注明細", SchemaFileLoaderResolver
				.loadTable(file.toFile(), "受注明細").getName());
		assertEquals("移行先", SchemaFileLoaderResolver
				.loadSchema(file, "移行先").getName());
		assertEquals("移行先", SchemaFileLoaderResolver
				.loadTable(file, "移行先", "受注明細").getSchemaName());
	}

	@Test
	void loadsOnlyViewCompatibleSavedQueries() {
		final Schema schema = new Schema("");
		MdbFileLoader.loadQueries(List.of(
				query("販売一覧", Query.Type.SELECT, false, List.of(),
						"SELECT * FROM [販売]"),
				query("販売統合", Query.Type.UNION, false, List.of(),
						"SELECT * FROM [国内] UNION SELECT * FROM [海外]"),
				query("非表示", Query.Type.SELECT, true, List.of(),
						"SELECT 1"),
				query("パラメータ付き", Query.Type.SELECT, false,
						List.of("PARAMETERS [対象日] DateTime"),
						"SELECT * FROM [販売]"),
				query("更新処理", Query.Type.UPDATE, false, List.of(),
						"UPDATE [販売] SET [状態] = 1")), schema);

		assertEquals(2, schema.getViews().size());
		assertEquals("SELECT * FROM [販売]", schema.getViews()
				.get("販売一覧").getDefinition().get(0));
		assertNotNull(schema.getViews().get("販売統合"));
	}

	@Test
	void mapsCommonAccessTypesAndValues() throws Exception {
		final Path file = tempDirectory.resolve("型対応.accdb");
		final LocalDateTime timestamp = LocalDateTime.of(2026, 8, 25, 12,
				34, 56);
		try (Database database = DatabaseBuilder.create(
				Database.FileFormat.V2019, file.toFile())) {
			final io.github.spannm.jackcess.Table source = new TableBuilder(
					"型一覧")
					.addColumn(new ColumnBuilder("真偽値",
							io.github.spannm.jackcess.DataType.BOOLEAN))
					.addColumn(new ColumnBuilder("バイト整数",
							io.github.spannm.jackcess.DataType.BYTE))
					.addColumn(new ColumnBuilder("短整数",
							io.github.spannm.jackcess.DataType.INT))
					.addColumn(new ColumnBuilder("長整数",
							io.github.spannm.jackcess.DataType.LONG))
					.addColumn(new ColumnBuilder("巨大整数",
							io.github.spannm.jackcess.DataType.BIG_INT))
					.addColumn(new ColumnBuilder("単精度",
							io.github.spannm.jackcess.DataType.FLOAT))
					.addColumn(new ColumnBuilder("倍精度",
							io.github.spannm.jackcess.DataType.DOUBLE))
					.addColumn(new ColumnBuilder("通貨",
							io.github.spannm.jackcess.DataType.MONEY))
					.addColumn(new ColumnBuilder("小数",
							io.github.spannm.jackcess.DataType.NUMERIC)
							.withPrecision(12).withScale(3))
					.addColumn(new ColumnBuilder("GUID",
							io.github.spannm.jackcess.DataType.GUID))
					.addColumn(new ColumnBuilder("日時",
							io.github.spannm.jackcess.DataType.SHORT_DATE_TIME))
					.addColumn(new ColumnBuilder("バイナリ",
							io.github.spannm.jackcess.DataType.BINARY)
							.withLength(8))
					.toTable(database);
			source.addRow(true, (byte) 12, (short) 1234, 123456,
					1234567890123L, 1.25f, 2.5d,
					new BigDecimal("1234.5678"),
					new BigDecimal("98765.432"),
					"{12345678-1234-1234-1234-1234567890AB}", timestamp,
					new byte[] { 4, 5, 6 });
		}

		final var table = MdbFileLoader.loadTable(file, "型一覧");
		assertEquals(DataType.BOOLEAN,
				table.getColumns().get("真偽値").getDataType());
		assertEquals(DataType.TINYINT,
				table.getColumns().get("バイト整数").getDataType());
		assertEquals(DataType.SMALLINT,
				table.getColumns().get("短整数").getDataType());
		assertEquals(DataType.INT,
				table.getColumns().get("長整数").getDataType());
		assertEquals(DataType.BIGINT,
				table.getColumns().get("巨大整数").getDataType());
		assertEquals(DataType.REAL,
				table.getColumns().get("単精度").getDataType());
		assertEquals(DataType.DOUBLE,
				table.getColumns().get("倍精度").getDataType());
		assertEquals(DataType.DECIMAL,
				table.getColumns().get("通貨").getDataType());
		assertEquals(DataType.DECIMAL,
				table.getColumns().get("小数").getDataType());
		assertEquals(12L, table.getColumns().get("小数").getLength());
		assertEquals(3, table.getColumns().get("小数").getScale());
		assertEquals(DataType.NVARCHAR,
				table.getColumns().get("GUID").getDataType());
		assertEquals(DataType.DATETIME,
				table.getColumns().get("日時").getDataType());
		assertEquals(DataType.VARBINARY,
				table.getColumns().get("バイナリ").getDataType());

		final var rows = table.getRows().iterator();
		assertTrue(rows.hasNext());
		final var row = rows.next();
		assertEquals(true, row.get("真偽値"));
		assertEquals(12, ((Number) row.get("バイト整数")).intValue());
		assertEquals(1234, ((Number) row.get("短整数")).intValue());
		assertEquals(123456, ((Number) row.get("長整数")).intValue());
		assertEquals(1234567890123L,
				((Number) row.get("巨大整数")).longValue());
		assertEquals(1.25f, ((Number) row.get("単精度")).floatValue());
		assertEquals(2.5d, ((Number) row.get("倍精度")).doubleValue());
		assertEquals(new BigDecimal("98765.432"), row.get("小数"));
		final Date dateTimeValue = row.get("日時");
		assertEquals(timestamp, LocalDateTime.ofInstant(
				dateTimeValue.toInstant(), ZoneId.systemDefault()));
		assertTrue(row.get("バイナリ") instanceof byte[]);
		assertFalse(rows.hasNext());
	}

	@Test
	void loadsCalculatedColumnExpression() throws Exception {
		final Path file = tempDirectory.resolve("計算列.accdb");
		try (Database database = DatabaseBuilder.create(
				Database.FileFormat.V2010, file.toFile())) {
			new TableBuilder("注文明細")
					.addColumn(new ColumnBuilder("単価",
							io.github.spannm.jackcess.DataType.LONG))
					.addColumn(new ColumnBuilder("数量",
							io.github.spannm.jackcess.DataType.LONG))
					.addColumn(new ColumnBuilder("金額",
							io.github.spannm.jackcess.DataType.LONG)
							.withCalculatedInfo("[単価] * [数量]"))
					.toTable(database);
		}

		final var table = MdbFileLoader.loadTable(file, "注文明細");
		assertEquals("[単価] * [数量]",
				table.getColumns().get("金額").getFormula());
	}

	private static Query query(final String name, final Query.Type type,
			final boolean hidden, final List<String> parameters,
			final String sql) {
		return new Query() {
			@Override
			public String getName() {
				return name;
			}

			@Override
			public Type getType() {
				return type;
			}

			@Override
			public boolean isHidden() {
				return hidden;
			}

			@Override
			public int getObjectId() {
				return 0;
			}

			@Override
			public int getObjectFlag() {
				return type.getObjectFlag();
			}

			@Override
			public List<String> getParameters() {
				return parameters;
			}

			@Override
			public String getOwnerAccessType() {
				return null;
			}

			@Override
			public String toSQLString() {
				return sql;
			}
		};
	}
}
