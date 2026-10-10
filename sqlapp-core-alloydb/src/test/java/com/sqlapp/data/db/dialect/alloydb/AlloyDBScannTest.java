/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.alloydb;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.alloydb.sql.AlloyDBCreateIndexFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class AlloyDBScannTest {
	private Table table() {
		var table = new Table("Vectors").setSchemaName("Mixed Schema");
		table.getColumns().add("Embedding", c -> c.setDataTypeName("vector(3)"));
		var i = new Index("Scann Index");
			i.getColumns().add("Embedding");
			i.setIndexType(IndexType.Vector);
			i.getSpecifics().put(AlloyDBCreateIndexFactory.METHOD, "scann");
			i.getSpecifics().put(AlloyDBCreateIndexFactory.KEYS, "\"Embedding\" public.cosine");
			i.getSpecifics().put(AlloyDBCreateIndexFactory.OPTIONS, "num_leaves='1'");
		table.getIndexes().add(i);
		return table;
	}

	@Test
	void scannSpecificsSurviveXmlAndGenerateQuotedDdl() throws Exception {
		for (int major : new int[] { 15, 16, 17 }) {
			var dialect = DialectResolver.getInstance().getDialect("AlloyDB", major, 0, null);
			Table table = SchemaUtils.readXml(new java.io.StringReader(table().asXml()));
			var index = table.getIndexes().get("Scann Index");
			com.sqlapp.data.db.sql.SqlFactory<Index> factory = dialect.createSqlFactoryRegistry().getSqlFactory(index, SqlType.CREATE);
			String sql = factory.createSql(index).get(0).getSqlText();
			assertTrue(sql.contains("USING scann (\"Embedding\" public.cosine)"), sql);
			assertTrue(sql.contains("num_leaves='1'"), sql);
			assertTrue(sql.contains("\"Scann Index\""), sql);
			assertTrue(sql.contains("\"Mixed Schema\".\"Vectors\""), sql);
			assertFalse(sql.contains("VECTOR"), sql);
		}
	}

	@Test
	void rejectsIncompleteAndUnsupportedConfiguration() {
		var dialect = new AlloyDB16();
		var index = table().getIndexes().get(0);
		com.sqlapp.data.db.sql.SqlFactory<Index> factory = dialect.createSqlFactoryRegistry().getSqlFactory(index, SqlType.CREATE);
		index.getSpecifics().remove(AlloyDBCreateIndexFactory.KEYS);
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(index));
		index.getSpecifics().put(AlloyDBCreateIndexFactory.METHOD, "unknown");
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(index));
	}
	@Test
	void createsNewVectorIndexFromColumnsAndDistanceWithoutSqlSpecifics() {
		var dialect = new AlloyDB16();
		var index = table().getIndexes().get(0);
		index.getSpecifics().clear();
		index.setVectorDistanceType(VectorDistanceType.Euclidean);
		com.sqlapp.data.db.sql.SqlFactory<Index> factory = dialect.createSqlFactoryRegistry().getSqlFactory(index, SqlType.CREATE);
		assertTrue(factory.createSql(index).get(0).getSqlText().contains("USING scann (\"Embedding\" l2)"));
		index.setTableSpaceName("Index Space");
		assertTrue(factory.createSql(index).get(0).getSqlText().contains("TABLESPACE \"Index Space\""));
		index.setUnique(true);
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(index));
		index.setUnique(false);
		index.setVectorDistanceType(VectorDistanceType.Hamming);
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(index));
	}

}
