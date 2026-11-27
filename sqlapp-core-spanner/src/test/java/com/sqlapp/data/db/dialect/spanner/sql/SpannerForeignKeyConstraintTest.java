/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class SpannerForeignKeyConstraintTest {
	@Test
	void ordersChangedForeignKeyAroundReferencedLocalColumnChanges() {
		final var table = schema().getTables().get("Orders");
		table.getColumns().add(new Column("other id").setDataType(DataType.BIGINT));
		final var target = table.clone();
		final var fk = (ForeignKeyConstraint) target.getConstraints().get(0);
		fk.getColumns().clear();
		fk.getColumns().add(target.getColumns().get("other id"));
		target.getColumns().remove("customer id");
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var operations = registry.createSql(table.diff(target), SqlType.ALTER);
		assertEquals(3, operations.size());
		assertTrue(operations.get(0).getSqlText().contains("DROP CONSTRAINT"));
		assertTrue(operations.get(1).getSqlText().contains("DROP COLUMN"));
		assertTrue(operations.get(2).getSqlText().contains("FOREIGN KEY ( `other id` )"));
	}

	@Test
	void preservesCompositeColumnOrderAndCrossSchemaIdentity() {
		final var parent = new Table("Parents").setSchemaName("Accounts");
		parent.getColumns().add(new Column("first").setDataType(DataType.BIGINT));
		parent.getColumns().add(new Column("second").setDataType(DataType.BIGINT));
		final var child = new Table("Children").setSchemaName("Sales");
		child.getColumns().add(new Column("a").setDataType(DataType.BIGINT));
		child.getColumns().add(new Column("b").setDataType(DataType.BIGINT));
		final var fk = new ForeignKeyConstraint("pair",
				new Column[] { child.getColumns().get(1), child.getColumns().get(0) },
				new Column[] { parent.getColumns().get(0), parent.getColumns().get(1) });
		child.getConstraints().add(fk);
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setQuateColumnName(true);
		registry.getOptions().setDecorateSchemaName(false);
		final String sql = registry.createSql(fk, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("FOREIGN KEY ( `b`, `a` ) REFERENCES `Accounts`.`Parents` ( `first`, `second` )"), sql);
	}

	private Schema schema() {
		final var schema = new Schema("Sales");
		schema.setDialect(DialectHolder.defaultDialect);
		final var parent = new Table("Customers");
		parent.getColumns().add(new Column("id").setDataType(DataType.BIGINT).setNotNull(true));
		parent.getConstraints().addPrimaryKeyConstraint("pk_customers", parent.getColumns().get(0));
		schema.getTables().add(parent);
		final var child = new Table("Orders");
		child.getColumns().add(new Column("customer id").setDataType(DataType.BIGINT));
		schema.getTables().add(child);
		child.getConstraints()
				.add(new ForeignKeyConstraint("Customer FK", child.getColumns().get(0), parent.getColumns().get(0))
						.setDeleteRule(CascadeRule.None).setUpdateRule(CascadeRule.None)
						.setMatchOption(ForeignKeyConstraint.MatchOption.Simple)
						.setDeferrability(Deferrability.NotDeferrable));
		return schema;
	}

	@Test
	void recreatesXmlForeignKeyWithoutInvalidNoneOrOnUpdateClauses() throws Exception {
		final Schema restored = SchemaUtils.readXml(new java.io.StringReader(schema().asXml()));
		final var table = restored.getTables().get("Orders");
		final var foreignKey = (ForeignKeyConstraint) table.getConstraints().get(0);
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		registry.getOptions().setQuateColumnName(true);
		final String standalone = registry.createSql(foreignKey, SqlType.CREATE).get(0).getSqlText();
		assertTrue(standalone.contains("ALTER TABLE `Sales`.`Orders` ADD CONSTRAINT `Customer FK`"), standalone);
		assertTrue(standalone.contains("FOREIGN KEY ( `customer id` ) REFERENCES `Sales`.`Customers` ( `id` )"),
				standalone);
		assertTrue(standalone.endsWith("ON DELETE NO ACTION"), standalone);
		assertFalse(standalone.contains("ON UPDATE"), standalone);
		assertFalse(standalone.contains("NONE"), standalone);
		final String create = registry.createSql(table, SqlType.CREATE).get(0).getSqlText();
		assertTrue(create.contains("ON DELETE NO ACTION"), create);
	}

	@Test
	void addsDropsAndChangesEnforcementThroughTableDiff() {
		final var schema = schema();
		final var table = schema.getTables().get("Orders");
		final var original = table.clone();
		original.getConstraints().clear();
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertEquals(1, registry.createSql(original.diff(table), SqlType.ALTER).size());
		final var dropped = registry.createSql(table.diff(original), SqlType.ALTER);
		assertEquals(1, dropped.size());
		assertTrue(dropped.get(0).getSqlText().contains("DROP CONSTRAINT `Customer FK`"));
		final var target = table.clone();
		((ForeignKeyConstraint) target.getConstraints().get(0)).setEnable(false);
		final var changes = registry.createSql(table.diff(target), SqlType.ALTER);
		assertEquals(2, changes.size());
		assertTrue(changes.get(0).getSqlText().contains("DROP CONSTRAINT"));
		assertTrue(changes.get(1).getSqlText().endsWith("NOT ENFORCED"), changes.get(1).getSqlText());
	}

	@Test
	void rendersSupportedDeleteActionsAndRejectsUnsupportedSemantics() {
		final var table = schema().getTables().get("Orders");
		final var fk = (ForeignKeyConstraint) table.getConstraints().get(0);
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		for (final var action : java.util.List.of(CascadeRule.Cascade, CascadeRule.SetNull)) {
			fk.setDeleteRule(action);
			assertTrue(registry.createSql(fk, SqlType.CREATE).get(0).getSqlText().endsWith("ON DELETE " + action));
			fk.setEnable(false);
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(fk, SqlType.CREATE));
			fk.setEnable(true);
		}
		fk.setDeleteRule(CascadeRule.Restrict);
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(fk, SqlType.CREATE));
		fk.setDeleteRule(CascadeRule.None).setUpdateRule(CascadeRule.Cascade);
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(fk, SqlType.CREATE));
		fk.setUpdateRule(CascadeRule.None).setMatchOption(ForeignKeyConstraint.MatchOption.Full);
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(fk, SqlType.CREATE));
		fk.setMatchOption(ForeignKeyConstraint.MatchOption.Simple).setDeferrability(Deferrability.InitiallyImmediate);
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(fk, SqlType.CREATE));
	}

	@Test
	void rejectsIncompleteKeysAndUnresolvedParentInsteadOfGuessingSelfReference() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var table = schema().getTables().get("Orders");
		final var fk = (ForeignKeyConstraint) table.getConstraints().get(0);
		fk.getRelatedColumns().clear();
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(fk, SqlType.CREATE));
		fk.getRelatedColumns().add("id");
		fk.setRelatedTableName("Missing");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(fk, SqlType.CREATE));
	}
}
