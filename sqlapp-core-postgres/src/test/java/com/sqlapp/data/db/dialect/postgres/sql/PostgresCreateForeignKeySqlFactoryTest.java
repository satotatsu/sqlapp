/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Table;

class PostgresCreateForeignKeySqlFactoryTest extends AbstractPostgresSqlFactoryTest {
	@Test
	void emitsNoActionAndPreservesOtherCascadeRules() {
		Table parent = new Table("parent");
		parent.getColumns().add("id", c -> c.setDataType(DataType.INT));
		Table child = new Table("child");
		child.getColumns().add("id", c -> c.setDataType(DataType.INT));
		ForeignKeyConstraint fk = child.getConstraints().addForeignKeyConstraint("fk",
				child.getColumns().get("id"), parent.getColumns().get("id"));
		SqlFactory<ForeignKeyConstraint> factory = sqlFactoryRegistry.getSqlFactory(fk, SqlType.CREATE);
		fk.setDeleteRule(CascadeRule.None).setUpdateRule(CascadeRule.None);
		String sql = factory.createSql(fk).get(0).getSqlText();
		assertTrue(sql.contains("ON DELETE NO ACTION"));
		assertTrue(sql.contains("ON UPDATE NO ACTION"));
		fk.setDeleteRule(CascadeRule.Cascade).setUpdateRule(CascadeRule.Restrict);
		sql = factory.createSql(fk).get(0).getSqlText();
		assertTrue(sql.contains("ON DELETE CASCADE"));
		assertTrue(sql.contains("ON UPDATE RESTRICT"));
	}
	@Test
	void preservesDeferrabilityForStandaloneAndInlineForeignKeys() {
		for (int major : new int[] {8, 11, 15, 18}) {
			var registry = com.sqlapp.data.db.dialect.DialectResolver.getInstance()
					.getDialect("postgres", major, 0, null).createSqlFactoryRegistry();
			Table parent = new Table("parent");
			parent.getColumns().add("id", c -> c.setDataType(DataType.INT));
			Table child = new Table("child");
			child.getColumns().add("id", c -> c.setDataType(DataType.INT));
			var fk = child.getConstraints().addForeignKeyConstraint("fk",
					child.getColumns().get("id"), parent.getColumns().get("id"));
			for (var mode : com.sqlapp.data.schemas.Deferrability.values()) {
				fk.setDeferrability(mode);
				for (String sql : java.util.List.of(
						registry.createSql(fk, SqlType.CREATE).get(0).getSqlText(),
						registry.createSql(child, SqlType.CREATE).get(0).getSqlText())) {
					if (mode == com.sqlapp.data.schemas.Deferrability.NotDeferrable) {
						assertFalse(sql.contains("DEFERRABLE"), sql);
					} else {
						assertTrue(sql.contains("DEFERRABLE " + mode.getSqlValue()), sql);
					}
				}
			}
		}
	}

}
