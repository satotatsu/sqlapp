/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;

class OracleIndexStateTest {
	@Test
	void partitionAndSubpartitionMetadataRetainUsability() throws Exception {
		OracleIndexReader reader = new OracleIndexReader(DialectHolder.oracle10gDialect);
		Partition parent = new Partition("P");
		SubPartition sub = new SubPartition("SP");
		reader.readPartitionState(row("N/A", "VISIBLE"), parent);
		reader.readPartitionState(row("UNUSABLE", "VISIBLE"), sub);
		assertEquals("N/A", parent.getSpecifics().get("oracle.index.status"));
		assertEquals("UNUSABLE", sub.getSpecifics().get("oracle.index.status"));
		SubPartition restored = SchemaUtils.readXml(new StringReader(sub.asXml()));
		assertEquals("UNUSABLE", restored.getSpecifics().get("oracle.index.status"));
	}

	private ExResultSet row(String status, String visibility) {
		Map<String, String> values = Map.of("STATUS", status, "VISIBILITY", visibility);
		return new ExResultSet(null) {
			@Override
			public String getString(String key) {
				return values.get(key);
			}

			@Override
			public Object getObject(String key) {
				return getString(key);
			}
		};
	}

	private Index index() {
		Table table = new Table("T");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		Index index = new Index("IX");
		index.getColumns().add("ID");
		table.getIndexes().add(index);
		return index;
	}

	private String create(Index index) {
		return DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(index, SqlType.CREATE).get(0)
				.getSqlText();
	}

	@Test
	void invisibleUnusableStateSurvivesXmlAndCreate() throws Exception {
		Index index = index();
		new Oracle11gIndexReader(DialectHolder.oracle26aiDialect).readState(row("UNUSABLE", "INVISIBLE"), index);
		Table restored = SchemaUtils.readXml(new StringReader(index.getTable().asXml()));
		String sql = create(restored.getIndexes().get("IX"));
		assertTrue(sql.endsWith("INVISIBLE UNUSABLE"), sql);
		assertTrue(index.isEnable(), "UNUSABLE must not be mapped to constraint enablement");
	}

	@Test
	void oldReaderDoesNotReadVisibility() throws Exception {
		Index index = index();
		new OracleIndexReader(DialectHolder.oracle10gDialect).readState(new ExResultSet(null) {
			@Override
			public String getString(String key) {
				assertEquals("STATUS", key);
				return "VALID";
			}

			@Override
			public Object getObject(String key) {
				return getString(key);
			}
		}, index);
		assertFalse(index.getSpecifics().containsKey("oracle.index.visibility"));
		assertFalse(create(index).contains("UNUSABLE"));
	}

	@Test
	void visibilityRejectsOldDialectAndInvalidValues() {
		Index index = index();
		index.getSpecifics().put("oracle.index.visibility", "INVISIBLE");
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.oracle10gDialect.createSqlFactoryRegistry().createSql(index, SqlType.CREATE));
		index.getSpecifics().put("oracle.index.visibility", "TYPO");
		assertThrows(IllegalArgumentException.class, () -> create(index));
	}

	@Test
	void unsupportedAndPartitionedStatusFailExplicitly() {
		Index index = index();
		index.getSpecifics().put("oracle.index.status", "BROKEN");
		assertThrows(IllegalArgumentException.class, () -> create(index));
		index.getSpecifics().put("oracle.index.status", "UNUSABLE");
		index.setPartitioning(new Partitioning());
		index.getSpecifics().put("oracle.index.locality", "LOCAL");
		assertThrows(IllegalArgumentException.class, () -> create(index));
	}

	@Test
	void readersResolveAcrossVersionBranches() {
		assertInstanceOf(Oracle11gIndexReader.class,
				new Oracle11gTableReader(DialectHolder.oracle11gDialect).newIndexReader());
		assertInstanceOf(Oracle11gIndexReader.class,
				new Oracle12cTableReader(DialectHolder.oracle12cDialect).newIndexReader());
		assertInstanceOf(Oracle11gIndexReader.class,
				new OracleMviewReader(DialectHolder.oracle12cDialect).newIndexReader());
		assertEquals(OracleIndexReader.class,
				new OracleMviewReader(DialectHolder.oracle10gDialect).newIndexReader().getClass());
	}
}
