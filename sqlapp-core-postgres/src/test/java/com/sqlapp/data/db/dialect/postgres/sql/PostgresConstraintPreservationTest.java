package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class PostgresConstraintPreservationTest {
	@Test
	void preservesStandaloneConstraintAndBackingIndexComments() {
		for (int major : new int[] { 8, 9, 11, 15, 18 }) {
			var registry = DialectResolver.getInstance().getDialect("postgres", major, 2, null)
					.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			Table parent = new Table("parent").setSchemaName("Quoted Schema");
			parent.getColumns().add("id", c -> c.setDataType(DataType.INT));
			Table child = new Table("child").setSchemaName("Quoted Schema");
			child.getColumns().add("id", c -> c.setDataType(DataType.INT));
			child.getConstraints().addForeignKeyConstraint("Parent FK", child.getColumns().get("id"),
					parent.getColumns().get("id"));
			child.getConstraints().add(new CheckConstraint("Positive Check", "id > 0"));
			var key = new UniqueConstraint("Unique Key", false);
			key.addColumn("id");
			child.getConstraints().add(key);
			for (var constraint : child.getConstraints()) {
				constraint.setRemarks("日本語 O'Brien");
				var operations = registry.createSql(constraint, SqlType.CREATE);
				assertEquals(2, operations.size());
				assertEquals(SqlType.CREATE, operations.get(0).getSqlType());
				assertEquals(SqlType.SET_COMMENT, operations.get(1).getSqlType());
				assertTrue(operations.get(1).getSqlText().contains("COMMENT ON CONSTRAINT"));
				assertTrue(operations.get(1).getSqlText().contains("\"Quoted Schema\".child"));
				assertTrue(operations.get(1).getSqlText().contains("O''Brien"));
				constraint.setRemarks(null);
				assertEquals(1, registry.createSql(constraint, SqlType.CREATE).size());
			}
			key.getIndex().setRemarks("index note");
			String comment = registry.createSql(key, SqlType.CREATE).get(1).getSqlText();
			assertTrue(comment.contains("COMMENT ON INDEX \"Quoted Schema\".\"Unique Key\""), comment);
			key.setIndexName("Backing Index");
			key.getIndex().setRemarks("renamed note");
			comment = registry.createSql(key, SqlType.CREATE).get(1).getSqlText();
			assertTrue(comment.contains("\"Unique Key\""), comment);
			assertFalse(comment.contains("\"Backing Index\""), comment);
		}
	}

	@Test
	void preservesNoInheritAndNotValidWithoutDuplicateTableComments() {
		for (int[] version : new int[][] { { 9, 1 }, { 9, 2 }, { 11, 0 }, { 15, 0 }, { 18, 0 } }) {
			var registry = DialectResolver.getInstance().getDialect("postgres", version[0], version[1], null)
					.createSqlFactoryRegistry();
			Table table = new Table("items");
			table.getColumns().add("id", c -> c.setDataType(DataType.INT));
			var check = new CheckConstraint("positive", "id > 0");
			table.getConstraints().add(check);
			check.getSpecifics().put(PostgresConstraintOptions.NO_INHERIT, "true");
			boolean supported = version[0] > 9 || version[1] >= 2;
			assertEquals(supported,
					registry.createSql(check, SqlType.CREATE).get(0).getSqlText().contains("NO INHERIT"));
			if (supported) {
				check.setRemarks("retained");
				check.getSpecifics().put(PostgresConstraintOptions.NOT_VALID, "true");
				var operations = registry.createSql(table, SqlType.CREATE);
				assertEquals(3, operations.size());
				assertTrue(operations.get(1).getSqlText().endsWith("NO INHERIT NOT VALID"));
				assertEquals(1, operations.stream().filter(op -> op.getSqlType() == SqlType.SET_COMMENT).count());
			}
			check.getSpecifics().put(PostgresConstraintOptions.NO_INHERIT, "invalid");
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(check, SqlType.CREATE));
		}
	}
}
