/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class SpannerManagedIndexTest {
	private Schema schema() {
		final var schema = new Schema("Sales");
		schema.setDialect(DialectHolder.defaultDialect);
		final var parent = new Table("Parents");
		parent.getColumns().add(new Column("id").setDataType(DataType.BIGINT).setNotNull(true));
		parent.getConstraints().addPrimaryKeyConstraint("pk", parent.getColumns().get(0));
		schema.getTables().add(parent);
		final var child = new Table("Children");
		child.getColumns().add(new Column("parent_id").setDataType(DataType.BIGINT));
		child.getConstraints()
				.add(new ForeignKeyConstraint("fk", child.getColumns().get(0), parent.getColumns().get(0))
						.setDeleteRule(CascadeRule.None).setUpdateRule(CascadeRule.None)
						.setMatchOption(ForeignKeyConstraint.MatchOption.Simple));
		final var backing = new Index("server_backing");
		backing.getColumns().add("parent_id");
		backing.getSpecifics().put(SpannerCreateIndexFactory.SPANNER_IS_MANAGED, true);
		child.getIndexes().add(backing);
		final var user = new Index("IDX_fk_user_named");
		user.getColumns().add("parent_id");
		user.getSpecifics().put(SpannerCreateIndexFactory.SPANNER_IS_MANAGED, false);
		child.getIndexes().add(user);
		schema.getTables().add(child);
		return schema;
	}

	@Test
	void recreatesForeignKeyAndUserIndexWithoutRecreatingManagedBackingThroughXml() throws Exception {
		final Schema restored = SchemaUtils.readXml(new java.io.StringReader(schema().asXml()));
		final var child = restored.getTables().get("Children");
		final var operations = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(child, SqlType.CREATE);
		assertEquals(2, operations.size());
		assertTrue(operations.get(0).getSqlText().contains("FOREIGN KEY"));
		assertTrue(operations.get(1).getSqlText().contains("IDX_fk_user_named"));
		assertTrue(operations.stream().noneMatch(o -> o.getSqlText().contains("server_backing")));
		assertEquals(2, child.getIndexes().size()); // metadata is retained, not removed or renamed
	}

	@Test
	void refusesDirectCreateDropAndManagedIndexDiffs() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var original = schema().getTables().get("Children");
		final var backing = original.getIndexes().get("server_backing");
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(backing, SqlType.CREATE));
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(backing, SqlType.DROP));
		for (int mode = 0; mode < 3; mode++) {
			final var target = original.clone();
			switch (mode) {
			case 0 -> target.getIndexes().remove("server_backing");
			case 1 -> target.getIndexes().get("server_backing").getIncludes().add("extra");
			case 2 -> target.getIndexes().get("server_backing").getSpecifics()
					.put(SpannerCreateIndexFactory.SPANNER_IS_MANAGED, false);
			}
			assertThrows(UnsupportedOperationException.class,
					() -> registry.createSql(original.diff(target), SqlType.ALTER));
		}
		final var target = original.clone();
		target.getColumns().get("parent_id").setDefaultValue("1");
		assertEquals(1, registry.createSql(original.diff(target), SqlType.ALTER).size());
		final var withoutForeignKey = original.clone();
		withoutForeignKey.getConstraints().clear();
		final var dropConstraint = registry.createSql(original.diff(withoutForeignKey), SqlType.ALTER);
		assertEquals(1, dropConstraint.size());
		assertTrue(dropConstraint.get(0).getSqlText().contains("DROP CONSTRAINT"));
	}

	@Test
	void rejectsMalformedManagedConfigurationRatherThanTreatingItAsUserOwned() {
		final var table = schema().getTables().get("Children");
		table.getIndexes().get("server_backing").getSpecifics().put(SpannerCreateIndexFactory.SPANNER_IS_MANAGED,
				"unknown");
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE));
	}
}
