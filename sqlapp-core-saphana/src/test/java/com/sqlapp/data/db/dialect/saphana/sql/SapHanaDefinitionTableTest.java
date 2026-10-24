/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.saphana.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class SapHanaDefinitionTableTest {
	@Test
	void missingDefinitionCannotSilentlyCreateAnUnpartitionedTable() throws Exception {
		var table = new Table("T");
		table.getSpecifics().put("PARTITIONING_REPRESENTATION", "DEFINITION");
		var restored = (Table) SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(restored, SqlType.CREATE));
		var target = restored.clone();
		target.getColumns().add("B", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(restored.diff(target), SqlType.ALTER));
		var ordinary = new Table("T");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(ordinary.diff(target), SqlType.ALTER));
	}

	@Test
	void emptyOrCommentOnlyDefinitionsFailExplicitly() {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		for (String definition : new String[] { "  \n ", "-- no DDL\n", "/* no DDL */" }) {
			var table = new Table("T");
			table.setDefinition(definition);
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE), definition);
		}
	}

	@Test
	void replaysCompleteDdlWithoutDuplicatingIndexesOrSplittingQuotedSemicolons() throws Exception {
		var table = new Table("T");
		table.setDefinition("CREATE COLUMN TABLE T (A NVARCHAR(10) DEFAULT ';'); CREATE INDEX IX ON T(A);");
		table.getIndexes().add(new Index("IX"));
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		var restored = (Table) SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		var operations = registry.createSql(restored, SqlType.CREATE);
		assertEquals(2, operations.size());
		assertTrue(operations.getFirst().getSqlText().contains("DEFAULT ';'"));
		var target = table.clone();
		target.getColumns().add("B", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table.diff(target), SqlType.ALTER));
		table.getSpecifics().put("PARTITIONING_REPRESENTATION", "DEFINITION");
		table.getSpecifics().put("DEFINITION_SOURCE_TABLE", "T");
		table.getSpecifics().put("DEFINITION_SOURCE_SCHEMA", "APP");
		table.setSchemaName("OTHER");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
	}

	@Test
	void columnPrivilegeRequestsFailInsteadOfExpandingTheirScope() {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		var grant = new ColumnPrivilege().setSchemaName("S").setObjectName("T").setColumnName("A").setGranteeName("U")
				.setPrivilege("SELECT");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(grant, SqlType.GRANT));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(grant, SqlType.REVOKE));
	}
}
