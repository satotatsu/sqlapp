/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.db.dialect.informix.util.InformixConstraintModes;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;

class InformixConstraintModesTest {
	private String sql(Object object) {
		return DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(object, SqlType.CREATE).get(0)
				.getSqlText();
	}

	@Test
	void preservesDisabledPrimaryUniqueCheckAndForeignKeysInCreateAndAlter() throws Exception {
		final Table table = new Table("items");
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		table.getColumns().add("value", c -> c.setDataType(DataType.INT));
		table.setPrimaryKey("items_pk", table.getColumns().get("id"));
		table.getConstraints().getPrimaryKeyConstraint().setEnable(false);
		final var unique = new com.sqlapp.data.schemas.UniqueConstraint("items_uq", false);
		unique.getColumns().add(table.getColumns().get("value"));
		unique.setEnable(false);
		table.getConstraints().add(unique);
		final var check = new CheckConstraint("items_ck", "value>0");
		check.setEnable(false);
		table.getConstraints().add(check);
		final Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
		final String create = sql(restored).replace("\"", "");
		for (String name : java.util.List.of("items_pk", "items_uq", "items_ck"))
			assertTrue(create.contains("CONSTRAINT " + name + " DISABLED"), create);
		assertTrue(sql(restored.getConstraints().get("items_ck")).contains("DISABLED"));
		assertTrue(sql(restored.getConstraints().get("items_uq")).contains("DISABLED"));
		final Table child = new Table("child");
		child.getColumns().add("id", c -> c.setDataType(DataType.INT));
		child.getConstraints()
				.addForeignKeyConstraint("child_fk", child.getColumns().get("id"), restored.getColumns().get("id"))
				.setEnable(false);
		assertTrue(
				sql(child.getConstraints().get("child_fk")).replace("\"", "").contains("CONSTRAINT child_fk DISABLED"));
	}

	@Test
	void retainsFilteringModesThroughXml() throws Exception {
		for (String state : java.util.List.of("E", "D", "F", "G")) {
			final Table table = new Table("items");
			table.getColumns().add("value", c -> c.setDataType(DataType.INT));
			final var check = new CheckConstraint("ck", "value>0");
			InformixConstraintModes.apply(check, state);
			table.getConstraints().add(check);
			final Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
			assertEquals(!"D".equals(state), restored.getConstraints().get("ck").isEnable());
			if (!"E".equals(state))
				assertTrue(sql(restored).contains(InformixConstraintModes.decode(state)));
		}
	}

	@Test
	void preservesNamedDisabledNotNullOnNullableColumns() throws Exception {
		final Table table = new Table("items");
		table.getColumns().add("value", c -> {
			c.setDataType(DataType.INT).setNullable(true);
			c.getSpecifics().put(InformixConstraintModes.NOT_NULL_NAME, "quoted\"nn");
			c.getSpecifics().put(InformixConstraintModes.NOT_NULL_OWNER, "other");
			c.getSpecifics().put(InformixConstraintModes.NOT_NULL_MODE, "DISABLED");
		});
		final Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
		assertFalse(restored.getColumns().get(0).isNotNull());
		assertTrue(sql(restored).contains("NOT NULL CONSTRAINT \"other\".\"quoted\"\"nn\" DISABLED"), sql(restored));
		final var constraint = new com.sqlapp.data.schemas.NotNullConstraint("named_nn");
		constraint.setEnable(false);
		constraint.setColumn(restored.getColumns().get(0));
		constraint.getSpecifics().put(com.sqlapp.data.db.dialect.informix.Informix.OBJECT_OWNER, "other");
		restored.getConstraints().add(constraint);
		final Table canonical = SchemaUtils.readXml(new StringReader(restored.asXml()));
		assertFalse(canonical.getColumns().get(0).isNotNull());
		assertTrue(sql(canonical).contains("NOT NULL CONSTRAINT \"other\".\"named_nn\" DISABLED"), sql(canonical));
	}

	@Test
	void createsStandaloneNotNullWithCompleteColumnDefinition() {
		final Table table = new Table("items");
		table.getColumns().add("value", c -> c.setDataType(DataType.INT).setDefaultValue("42"));
		final var constraint = new com.sqlapp.data.schemas.NotNullConstraint("nn");
		constraint.setEnable(false);
		constraint.setColumn(table.getColumns().get(0));
		table.getConstraints().add(constraint);
		assertEquals("ALTER TABLE items MODIFY ( value INTEGER DEFAULT 42 NOT NULL CONSTRAINT nn DISABLED)",
				sql(constraint).replace("\"", ""));
		assertThrows(IllegalArgumentException.class,
				() -> sql(new com.sqlapp.data.schemas.NotNullConstraint("unresolved")));
	}

	@Test
	void rejectsUnknownAndContradictoryModes() {
		final var check = new CheckConstraint("ck", "value>0");
		assertThrows(IllegalStateException.class, () -> InformixConstraintModes.apply(check, "?"));
		check.getSpecifics().put(InformixConstraintModes.MODE, "invalid");
		assertThrows(IllegalArgumentException.class, () -> InformixConstraintModes.sql(check));
		InformixConstraintModes.apply(check, "F");
		check.setEnable(false);
		assertThrows(IllegalArgumentException.class, () -> InformixConstraintModes.sql(check));
		assertEquals("ENABLED", InformixConstraintModes.decode(null));
	}
}
