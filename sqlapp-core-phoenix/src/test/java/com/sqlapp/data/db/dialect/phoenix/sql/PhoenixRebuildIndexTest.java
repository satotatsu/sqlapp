/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.phoenix.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class PhoenixRebuildIndexTest {
	@Test
	void rebuildsTableAndViewIndexesThroughTheOrdinaryRegistryWithoutRecreation() throws Exception {
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.defaultDialect4_8,
				DialectHolder.defaultDialect4_9, DialectHolder.defaultDialect5_3, DialectHolder.defaultDialect5_3_1)) {
			for (boolean view : new boolean[] { false, true }) {
				final Table table = view ? new View("Order Details") : new Table("Order Details");
				table.setSchemaName("Sales");
				final var index = new Index("Index Details");
				index.getSpecifics().put("phoenix.indexState", "DISABLE");
				table.getIndexes().add(index);
				final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
				final var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				final var operations = registry.createSql(restored.getIndexes().get(0), SqlType.REBUILD);
				assertEquals(1, operations.size());
				assertEquals(SqlType.REBUILD, operations.get(0).getSqlType());
				assertEquals("ALTER INDEX \"Index Details\" ON \"Sales\".\"Order Details\" REBUILD",
						operations.get(0).getSqlText());
				assertEquals("DISABLE", restored.getIndexes().get(0).getSpecifics().get("phoenix.indexState"));
				final Table target = restored.clone();
				assertEquals(operations.get(0).getSqlText(), registry
						.createSql(restored.getIndexes().get(0).diff(target.getIndexes().get(0)), SqlType.REBUILD)
						.get(0).getSqlText());
			}
		}
	}

	@Test
	void rejectsMissingNamesOrParentsRatherThanGeneratingAnEmptyOperation() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(new Index("idx"), SqlType.REBUILD));
		for (String name : new String[] { null, "", " " }) {
			final var table = new Table("T");
			final var index = new Index(name);
			table.getIndexes().add(index);
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(index, SqlType.REBUILD));
		}
		final var table = new Table(" ");
		final var index = new Index("idx");
		table.getIndexes().add(index);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(index, SqlType.REBUILD));
	}
}
