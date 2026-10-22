/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.saphana.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class SapHanaIndexSynonymTest {
	@Test
	void placesIndexTypeBeforeIndexNameAndQualifiesTable() {
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.cloudDialect)) {
			for (IndexType type : List.of(IndexType.BTree, IndexType.CPBTree, IndexType.InvertedValue,
					IndexType.InvertedHash, IndexType.InvertedIndivisual)) {
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				Table table = new Table("ITEMS").setSchemaName("Odd Schema");
				table.getColumns().add("ID", c -> c.setDataType(DataType.INT));
				Index index = new Index("IX_ITEMS", table.getColumns().get("ID")).setIndexType(type).setUnique(true);
				table.getIndexes().add(index);
				String sql = registry.createSql(index, SqlType.CREATE).getFirst().getSqlText().replaceAll("\\s+", " ");
				assertTrue(
						sql.startsWith(
								"CREATE UNIQUE " + type.getSqlValue() + " INDEX IX_ITEMS ON \"Odd Schema\".ITEMS"),
						sql);
				assertFalse(sql.contains("IF NOT EXISTS"), sql);
			}
		}
	}

	@Test
	void rejectsUnsupportedAndCloudNonUniqueHashIndexes() {
		Table table = new Table("ITEMS");
		table.getColumns().add("ID", c -> c.setDataType(DataType.INT));
		Index index = new Index("IX_ITEMS", table.getColumns().get("ID")).setIndexType(IndexType.Gin);
		table.getIndexes().add(index);
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(index, SqlType.CREATE));
		index.setIndexType(IndexType.InvertedHash);
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.cloudDialect.createSqlFactoryRegistry().createSql(index, SqlType.CREATE));
		assertFalse(DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(index, SqlType.CREATE).isEmpty());
	}

	@Test
	void createsQuotedSynonymsAndRejectsMissingTargets() {
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.cloudDialect)) {
			var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			Synonym synonym = new Synonym("ITEM_ALIAS").setSchemaName("APP").setObjectSchemaName("Odd Schema")
					.setObjectName("A\"B");
			assertEquals("CREATE SYNONYM APP.ITEM_ALIAS FOR \"Odd Schema\".\"A\"\"B\"",
					registry.createSql(synonym, SqlType.CREATE).getFirst().getSqlText());
			synonym.setObjectName(null);
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(synonym, SqlType.CREATE));
		}
	}
}
