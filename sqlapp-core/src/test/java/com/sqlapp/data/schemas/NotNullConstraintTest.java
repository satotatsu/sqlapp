package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

class NotNullConstraintTest {
	@Test
	void testXmlCloneAndColumnRename() throws Exception {
		Catalog catalog = new Catalog("CAT");
		Schema schema = new Schema("PUBLIC");
		Table table = new Table("CUSTOMERS");
		Column column = new Column("CUSTOMER_ID");
		catalog.getSchemas().add(schema);
		schema.getTables().add(table);
		table.getColumns().add(column);
		NotNullConstraint constraint = new NotNullConstraint(
				"NN_CUSTOMERS_CUSTOMER_ID", column)
				.setNoInherit(true).setValidated(false);
		table.getConstraints().add(constraint);

		column.setName("ID");
		assertEquals("ID", constraint.getColumnName());
		assertSame(column, constraint.getColumn());
		assertTrue(column.isNotNull());
		assertEquals(constraint, constraint.clone());

		StringWriter writer = new StringWriter();
		catalog.writeXml(writer);
		Catalog restored = new Catalog();
		restored.loadXml(new StringReader(writer.toString()));
		NotNullConstraint restoredConstraint = restored.getSchemas()
				.get("PUBLIC").getTables().get("CUSTOMERS").getConstraints()
				.getNotNullConstraints().get(0);
		assertEquals("ID", restoredConstraint.getColumnName());
		assertTrue(restoredConstraint.isNoInherit());
		assertTrue(!restoredConstraint.isValidated());
		assertSame(restored.getSchemas().get("PUBLIC").getTables()
				.get("CUSTOMERS").getColumns().get("ID"),
				restoredConstraint.getColumn());
	}
}
