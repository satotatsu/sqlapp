/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.virtica.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class VirticaIndexSqlTest {
	private Table table() {
		Table table = new Table("source table");
		table.setSchemaName("source schema");
		table.getColumns().add("id", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT));
		table.getColumns().add("body", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.VARCHAR));
		Index i = new Index("text index");
		i.setIndexType(IndexType.FullText);
		i.getColumns().add("id");
		i.getColumns().add("body");
		i.getSpecifics().put("INDEX_SCHEMA_NAME", "index schema");
		table.getIndexes().add(i);
		return table;
	}

	@Test
	void textIndexUsesVendorSyntaxThroughDirectAndTableCreateAfterXml() throws Exception {
		var registry = DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0).createSqlFactoryRegistry();
		Table restored = SchemaUtils.readXml(new java.io.StringReader(table().asXml()));
		String sql = registry.createSql(restored.getIndexes().get(0), SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("CREATE TEXT INDEX \"index schema\".\"text index\""), sql);
		assertTrue(sql.contains("ON \"source schema\".\"source table\""), sql);
		assertTrue(sql.contains("\"id\", \"body\""), sql);
		assertTrue(registry.createSql(restored, SqlType.CREATE).stream().anyMatch(o -> o.getSqlText().equals(sql)));
	}

	@Test
	void incompleteCatalogAndUnsupportedIndexOptionsFailClearly() {
		var registry = DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0).createSqlFactoryRegistry();
		Index index = table().getIndexes().get(0);
		index.getSpecifics().put("TOKENIZER_NAME", "custom");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(index, SqlType.CREATE));
		index.getSpecifics().remove("TOKENIZER_NAME");
		index.getColumns().remove(0);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(index, SqlType.CREATE));
		index.setIndexType(IndexType.BTree);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(index, SqlType.CREATE));
	}

	@Test
	void indexOptionsCannotBeSilentlyDiscarded() {
		var registry = DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0).createSqlFactoryRegistry();
		Index index = table().getIndexes().get(0);
		index.setUnique(true);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(index, SqlType.CREATE));
		index.setUnique(false);
		index.getIncludes().add("id");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(index, SqlType.CREATE));
		index.getIncludes().clear();
		index.getColumns().get(0).setOrder(Order.Desc);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(index, SqlType.CREATE));
		index.getColumns().get(0).setOrder(Order.Asc);
		index.getSpecifics().put("STEMMER_NAME", "custom");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(index, SqlType.CREATE));
	}
}
