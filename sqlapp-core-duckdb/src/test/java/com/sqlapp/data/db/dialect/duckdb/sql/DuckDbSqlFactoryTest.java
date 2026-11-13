/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.duckdb.DialectHolder;
import com.sqlapp.data.db.dialect.duckdb.metadata.DuckDbIndexReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;

class DuckDbSqlFactoryTest {

	private final SqlFactoryRegistry registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();

	private static Table parent() {
		final Schema schema = new Schema("s1");
		final Table table = new Table("parent");
		schema.getTables().add(table);
		table.getColumns().add(new Column("id").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("name").setDataType(DataType.VARCHAR).setDefaultValue("'x'"));
		table.getColumns().add(new Column("twice").setDataType(DataType.INT).setFormula("(id * 2)"));
		table.getColumns().add(new Column("ci").setDataType(DataType.VARCHAR).setCollation("nocase"));
		table.setPrimaryKey("pk_parent", table.getColumns().get("id"));
		table.setRemarks("parent table");
		return table;
	}

	private static String join(final List<SqlOperation> operations) {
		final StringBuilder builder = new StringBuilder();
		for (final SqlOperation operation : operations) {
			builder.append(operation.getSqlText()).append(";\n");
		}
		return builder.toString();
	}

	@Test
	void createsTableWithGeneratedColumnCollationAndComment() {
		final String sql = join(registry.createSql(parent(), SqlType.CREATE));
		assertTrue(sql.contains("twice INTEGER GENERATED ALWAYS AS ((id * 2)) VIRTUAL"), sql);
		assertTrue(sql.contains("ci VARCHAR COLLATE nocase"), sql);
		assertTrue(sql.contains("name VARCHAR DEFAULT 'x'"), sql);
		assertTrue(sql.contains("CONSTRAINT pk_parent PRIMARY KEY ( id )"), sql);
		assertTrue(sql.contains("COMMENT ON TABLE s1.parent IS 'parent table'"), sql);
	}

	@Test
	void rejectsIdentityAndCascadingForeignKeys() {
		final Table identity = parent();
		identity.getColumns().get("id").setIdentity(true);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(identity, SqlType.CREATE));

		final Table parent = parent();
		final Table child = new Table("child");
		parent.getParent().add(child);
		child.getColumns().add(new Column("pid").setDataType(DataType.INT));
		child.getConstraints()
				.addForeignKeyConstraint("fk_child", child.getColumns().get("pid"), parent.getColumns().get("id"))
				.setDeleteRule(CascadeRule.Cascade);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(child, SqlType.CREATE));
	}

	@Test
	void createsIndexesWithExpressionKeys() {
		final Table table = parent();
		final Index plain = new Index("ix_name");
		plain.getColumns().add("name", Order.Desc);
		table.getIndexes().add(plain);
		assertEquals("CREATE INDEX IF NOT EXISTS ix_name ON s1.parent ( name DESC)",
				registry.createSql(plain, SqlType.CREATE).get(0).getSqlText());
		final Index expression = new Index("ix_expr");
		expression.getColumns().add("(id + 1)", Order.Asc);
		expression.getSpecifics().put(DuckDbIndexReader.INDEX_KEYS, "(id + 1), lower(name)");
		table.getIndexes().add(expression);
		assertEquals("CREATE INDEX IF NOT EXISTS ix_expr ON s1.parent ( (id + 1), lower(name))",
				registry.createSql(expression, SqlType.CREATE).get(0).getSqlText());
	}

	@Test
	void createsSequencesWithoutCacheAndReservesValues() {
		final Schema schema = new Schema("s1");
		final Sequence sequence = new Sequence("seq1");
		schema.getSequences().add(sequence);
		sequence.setStartValue(10);
		sequence.setIncrementBy(2);
		sequence.setMinValue(1);
		sequence.setMaxValue(1000);
		sequence.setCycle(true);
		sequence.setCacheSize(20);
		assertEquals("CREATE SEQUENCE IF NOT EXISTS s1.seq1 START WITH 10 INCREMENT BY 2 MAXVALUE 1000 MINVALUE 1 CYCLE",
				registry.createSql(sequence, SqlType.CREATE).get(0).getSqlText());
		final String next = registry.createSql(sequence, SqlType.SEQUENCE_NEXT_VALUES).get(0).getSqlText();
		assertTrue(next.contains("nextval('s1.seq1')"), next);
		assertTrue(next.contains("GENERATE_SERIES"), next);
	}

	@Test
	void mergeRequiresDuckDb14AndUsesUnqualifiedSetColumns() {
		final String merge = registry.createSql(parent(), SqlType.MERGE).get(0).getSqlText();
		assertTrue(merge.startsWith("MERGE INTO s1.parent"), merge);
		assertTrue(merge.contains("SET name = _source_.name"), merge);
		final SqlFactoryRegistry old = DialectHolder.duckdb12.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class, () -> old.createSql(parent(), SqlType.MERGE));
	}
}
