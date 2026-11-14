/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-drsum.
 *
 * sqlapp-core-drsum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-drsum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-drsum.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.drsum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.drsum.resolver.DrSumDialectResolver;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.data.schemas.View;

/** DDL generation and resolution tests; no Dr.Sum server is used. */
class DrSumSqlTest {

	private final Dialect dialect = DialectHolder.defaultDialect;

	private List<String> sql(final Object obj, final SqlType type) {
		return dialect.createSqlFactoryRegistry().createSql(obj, type).stream().map(SqlOperation::getSqlText)
				.collect(Collectors.toList());
	}

	private static String flat(final String sql) {
		return sql.replaceAll("\\s+", " ").replace("( ", "(").replace(" ,", ",").replace(" )", ")").trim();
	}

	@Test
	void resolvesDrSumProductNames() {
		final DrSumDialectResolver resolver = new DrSumDialectResolver();
		for (final String name : List.of("Dr.Sum", "DrSum", "Dr Sum", "Dr.Sum EA", "dr.sum")) {
			assertInstanceOf(DrSum.class, resolver.getDialect(name, 5, 7), name);
		}
		assertNull(resolver.getDialect("Oracle", 19, 0));
		assertNull(resolver.getDialect("DrSumX", 5, 7));
		assertInstanceOf(DrSum.class, DialectResolver.getInstance().getDialect(FakeJdbc.metadata("Dr.Sum", "5.7")));
	}

	@Test
	void mapsTypesToDrSumSyntax() {
		final Table table = new Table("T");
		table.getColumns().add(new Column("I").setDataType(DataType.INT));
		table.getColumns().add(new Column("S").setDataType(DataType.SMALLINT));
		table.getColumns().add(new Column("B").setDataType(DataType.BIGINT));
		table.getColumns().add(new Column("V").setDataType(DataType.VARCHAR).setLength(20));
		table.getColumns().add(new Column("C").setDataType(DataType.CHAR).setLength(10));
		table.getColumns().add(new Column("W").setDataType(DataType.VARCHAR).setLength(1000));
		table.getColumns().add(new Column("N").setDataType(DataType.NUMERIC).setLength(10).setScale(2));
		table.getColumns().add(new Column("NF").setDataType(DataType.NUMERIC));
		table.getColumns().add(new Column("R").setDataType(DataType.REAL));
		table.getColumns().add(new Column("DB").setDataType(DataType.DOUBLE));
		table.getColumns().add(new Column("D").setDataType(DataType.DATE));
		table.getColumns().add(new Column("TM").setDataType(DataType.TIME).setLength(3));
		table.getColumns().add(new Column("TS").setDataType(DataType.TIMESTAMP));
		table.getColumns().add(new Column("TS6").setDataType(DataType.TIMESTAMP).setLength(6));
		table.getColumns().add(new Column("IV").setDataType(DataType.INTERVAL_DAY_TO_SECOND));
		final String sql = flat(sql(table, SqlType.CREATE).get(0));
		for (final String expected : List.of("I INTEGER", "S INTEGER", "B NUMERIC(19,0)", "V VARCHAR(20)",
				"C VARCHAR(10)", "W VARCHAR,", "N NUMERIC(10,2)", "NF NUMERIC,", "R REAL", "DB DOUBLE", "D DATE",
				"TM TIME(3)", "TS TIMESTAMP,", "TS6 TIMESTAMP(6)", "IV INTERVAL")) {
			assertTrue(sql.contains(expected), expected + " in " + sql);
		}
	}

	@Test
	void translatesKeysIndexesAndComments() {
		final Table table = new Table("売上");
		final Column id = new Column("id").setDataType(DataType.INT);
		final Column code = new Column("code").setDataType(DataType.VARCHAR).setLength(10).setNotNull(true);
		final Column branch = new Column("branch").setDataType(DataType.VARCHAR).setLength(10);
		final Column qty = new Column("数量").setDataType(DataType.INT).setDefaultValue("0").setRemarks("数量です");
		final Column day = new Column("day").setDataType(DataType.DATE);
		table.getColumns().add(id);
		table.getColumns().add(code);
		table.getColumns().add(branch);
		table.getColumns().add(qty);
		table.getColumns().add(day);
		table.setRemarks("売上テーブル");
		table.setPrimaryKey("PK_URIAGE", id);
		table.getConstraints().addUniqueConstraint("CK_CODE", code, branch);
		table.getIndexes().add("IX_DAY", day);
		final List<String> sqls = sql(table, SqlType.CREATE);
		final String create = flat(sqls.get(0));
		assertTrue(create.startsWith("CREATE TABLE [売上]"), create);
		assertTrue(create.contains("id INTEGER NOT NULL UNIQUE"), create);
		assertTrue(create.contains("code VARCHAR(10) NOT NULL,"), create);
		assertTrue(create.contains("[数量] INTEGER DEFAULT 0"), create);
		assertTrue(create.contains("COMPOUND KEY CK_CODE(code, branch) UNIQUE"), create);
		assertFalse(create.contains("PRIMARY KEY"), create);
		assertEquals("CREATE INDEX IX_DAY ON [売上](day)", flat(sqls.get(1)));
		assertEquals("ALTER TABLE [売上] MODIFY COMMENT '売上テーブル'", flat(sqls.get(2)));
		assertEquals("ALTER TABLE [売上] MODIFY [数量] COMMENT '数量です'", flat(sqls.get(3)));
		assertEquals(4, sqls.size());
		assertEquals("DROP INDEX IX_DAY", flat(sql(table.getIndexes().get("IX_DAY"), SqlType.DROP).get(0)));
		assertEquals("DROP TABLE [売上]", flat(sql(table, SqlType.DROP).get(0)));
	}

	@Test
	void translatesMultiColumnPrimaryKeyAndKeyChanges() {
		final Table table = new Table("T");
		final Column a = new Column("A").setDataType(DataType.INT).setNotNull(true);
		final Column b = new Column("B").setDataType(DataType.VARCHAR).setLength(5).setNotNull(true);
		table.getColumns().add(a);
		table.getColumns().add(b);
		table.setPrimaryKey("PK_T", a, b);
		assertTrue(flat(sql(table, SqlType.CREATE).get(0)).contains("COMPOUND KEY PK_T(A, B) NOT NULL UNIQUE"));
		final UniqueConstraint pk = table.getPrimaryKeyConstraint();
		assertEquals("ALTER TABLE T ADD CONSTRAINT COMPOUND KEY PK_T(A, B) NOT NULL UNIQUE",
				flat(sql(pk, SqlType.CREATE).get(0)));
		assertEquals("ALTER TABLE T DROP CONSTRAINT PK_T", flat(sql(pk, SqlType.DROP).get(0)));
		final UniqueConstraint single = new UniqueConstraint("UK_B", b);
		table.getConstraints().add(single);
		assertEquals("ALTER TABLE T ALTER B VARCHAR(5) UNIQUE NOT NULL", flat(sql(single, SqlType.CREATE).get(0)));
		assertEquals("ALTER TABLE T ALTER B VARCHAR(5) NOT NULL", flat(sql(single, SqlType.DROP).get(0)));
	}

	@Test
	void rejectsWhatDrSumCannotExpress() {
		assertRejected(table -> {
			final Table parent = new Table("P");
			parent.getColumns().add(new Column("ID").setDataType(DataType.INT));
			table.getConstraints().addForeignKeyConstraint("FK", table.getColumns().get("A"),
					parent.getColumns().get("ID"));
		});
		assertRejected(table -> table.getConstraints().addCheckConstraint("CK", "A > 0"));
		assertRejected(table -> table.getColumns().get("A").setIdentity(true));
		assertRejected(table -> table.getColumns().add(new Column("BIN").setDataType(DataType.VARBINARY).setLength(10)));
		assertRejected(table -> table.getColumns().add(new Column("TX").setDataType(DataType.VARCHAR).setLength(70000)));
		assertRejected(table -> table.getColumns().add(new Column("T6").setDataType(DataType.TIMESTAMP).setLength(9)));
		assertRejected(table -> table.getIndexes().add(new Index("UX", table.getColumns().get("A")).setUnique(true)));
		assertRejected(table -> table.getIndexes()
				.add(new Index("MX", table.getColumns().get("A"), table.getColumns().get("B"))));
		assertRejected(table -> table.getConstraints().addUniqueConstraint("A", table.getColumns().get("A"),
				table.getColumns().get("B")));
		assertRejected(table -> table.setRemarks("line1\nline2"));
		final Table table = baseTable();
		table.getColumns().get("A").setIdentity(false);
		assertThrows(UnsupportedOperationException.class, () -> sql(table, SqlType.ALTER));
	}

	private void assertRejected(final java.util.function.Consumer<Table> change) {
		final Table table = baseTable();
		change.accept(table);
		assertThrows(IllegalArgumentException.class, () -> sql(table, SqlType.CREATE));
	}

	private static Table baseTable() {
		final Table table = new Table("T");
		table.getColumns().add(new Column("A").setDataType(DataType.INT));
		table.getColumns().add(new Column("B").setDataType(DataType.INT));
		return table;
	}

	@Test
	void createsViewFromStatement() {
		final View view = new View("v1");
		view.setStatement(List.of("SELECT id FROM 売上"));
		assertEquals("CREATE VIEW v1 AS SELECT id FROM 売上", flat(sql(view, SqlType.CREATE).get(0)));
		assertEquals("DROP VIEW v1", flat(sql(view, SqlType.DROP).get(0)));
	}
}
