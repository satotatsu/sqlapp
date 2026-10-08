/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.CheckConstraint;

class PostgresNotValidConstraintTest {
	@Test
	void separatesNotValidConstraintsAndCommentsAfterTableCreation() {
		for (int major : new int[] {11,15,18}) {
			var registry = DialectResolver.getInstance().getDialect("postgres",major,0,null).createSqlFactoryRegistry();
			var parent = new Table("parent"); parent.getColumns().add("id", c -> c.setDataType(DataType.INT));
			var child = new Table("child"); child.getColumns().add("id", c -> c.setDataType(DataType.INT));
			var fk = child.getConstraints().addForeignKeyConstraint("parent_fk",child.getColumns().get("id"),parent.getColumns().get("id"));
			var check = new CheckConstraint("positive", "id > 0"); child.getConstraints().add(check);
			for (var constraint : child.getConstraints()) {
				constraint.getSpecifics().put(PostgresConstraintOptions.NOT_VALID,"true"); constraint.setRemarks("retained");
				assertTrue(registry.createSql(constraint,SqlType.CREATE).get(0).getSqlText().endsWith("NOT VALID"));
			}
			var operations = registry.createSql(child,SqlType.CREATE);
			assertEquals(5,operations.size());
			assertFalse(operations.get(0).getSqlText().contains("CONSTRAINT"));
			assertTrue(operations.get(1).getSqlText().endsWith("NOT VALID"));
			assertTrue(operations.get(2).getSqlText().endsWith("NOT VALID"));
			assertTrue(operations.get(3).getSqlText().startsWith("COMMENT"));
			assertEquals(2,child.getConstraints().size());
			assertEquals("true",fk.getSpecifics().get(PostgresConstraintOptions.NOT_VALID));
		}
	}

	@Test
	void respectsVersionBoundariesAndRejectsMalformedFlags() {
		for (int minor : new int[] {0,1,2}) {
			var registry = DialectResolver.getInstance().getDialect("postgres",9,minor,null).createSqlFactoryRegistry();
			var parent = new Table("parent"); parent.getColumns().add("id", c -> c.setDataType(DataType.INT));
			var child = new Table("child"); child.getColumns().add("id", c -> c.setDataType(DataType.INT));
			var fk = child.getConstraints().addForeignKeyConstraint("fk",child.getColumns().get("id"),parent.getColumns().get("id"));
			var check = new CheckConstraint("positive","id > 0"); child.getConstraints().add(check);
			fk.getSpecifics().put(PostgresConstraintOptions.NOT_VALID,"true");
			if (minor >= 1) assertTrue(registry.createSql(fk,SqlType.CREATE).get(0).getSqlText().contains("NOT VALID"));
			else assertThrows(IllegalArgumentException.class, () -> registry.createSql(fk,SqlType.CREATE));
			fk.getSpecifics().clear(); check.getSpecifics().put(PostgresConstraintOptions.NOT_VALID,"true");
			if (minor >= 2) assertEquals(2,registry.createSql(child,SqlType.CREATE).size());
			else assertThrows(IllegalArgumentException.class, () -> registry.createSql(child,SqlType.CREATE));
			check.getSpecifics().put(PostgresConstraintOptions.NOT_VALID,"invalid");
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(check,SqlType.CREATE));
			check.getSpecifics().put(PostgresConstraintOptions.NOT_VALID,"false");
			assertEquals(1,registry.createSql(child,SqlType.CREATE).size());
		}
	}
}
