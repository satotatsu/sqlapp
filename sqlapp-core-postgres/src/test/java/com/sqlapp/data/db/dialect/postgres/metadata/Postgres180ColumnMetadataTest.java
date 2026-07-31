package com.sqlapp.data.db.dialect.postgres.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class Postgres180ColumnMetadataTest {
	@Test
	void testRestoreNamedNotNullConstraint() {
		Column column = new Column("CUSTOMER_ID").setNotNull(true);
		Postgres180ColumnMetadata.applyNamedNotNull(column,
				"NN_CUSTOMERS_CUSTOMER_ID");
		Table table = table(column);
		Postgres180ColumnMetadata.moveNamedNotNullConstraints(table);
		assertTrue(column.isNotNull());
		assertEquals("NN_CUSTOMERS_CUSTOMER_ID",
				table.getConstraints().getNotNullConstraints().get(0).getName());
	}

	@Test
	void testIgnoreUnnamedNotNullConstraint() {
		Column column = new Column("CUSTOMER_ID").setNotNull(true);
		Postgres180ColumnMetadata.applyNamedNotNull(column, null);
		Table table = table(column);
		Postgres180ColumnMetadata.moveNamedNotNullConstraints(table);
		assertTrue(column.isNotNull());
		assertTrue(table.getConstraints().getNotNullConstraints().isEmpty());
	}

	@Test
	void testRestorePostgresSpecificConstraintState() {
		Column column = new Column("CUSTOMER_ID").setNotNull(true);
		Postgres180ColumnMetadata.applyNamedNotNull(column,
				"NN_CUSTOMERS_CUSTOMER_ID", true, false);
		Table table = table(column);
		Postgres180ColumnMetadata.moveNamedNotNullConstraints(table);
		assertTrue(table.getConstraints().getNotNullConstraints().get(0)
				.isNoInherit());
		assertTrue(!table.getConstraints().getNotNullConstraints().get(0)
				.isValidated());
	}

	private Table table(Column column) {
		Table table = new Table("CUSTOMERS");
		table.getColumns().add(column);
		return table;
	}
}
