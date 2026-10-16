/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;

class OracleIndexPartitionCompressionTest {
	private void read(String mode, AbstractPartition<?> partition) throws Exception {
		new OracleIndexReader(DialectHolder.oracle26aiDialect).readPartitionCompression(new ExResultSet(null) {
			@Override public String getString(String key) { assertEquals("COMPRESSION", key); return mode; }
			@Override public Object getObject(String key) { return getString(key); }
		}, partition);
	}
	private Index index(boolean local) {
		Table table = new Table("T"); table.getColumns().add("ID"); table.getColumns().add("CODE");
		Index index = new Index("IX"); table.getIndexes().add(index); index.getColumns().add("ID"); index.getColumns().add("CODE");
		Partitioning info = new Partitioning().setPartitioningType(PartitioningType.Range);
		info.getPartitioningColumns().add("ID");
		info.getPartitions().add(new Partition("P1").setHighValue("100").setTableSpaceName("Data Space"));
		index.setPartitioning(info);
		if (local) index.getSpecifics().put("oracle.index.locality", "LOCAL");
		return index;
	}
	private String create(Index index) {
		return DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(index, SqlType.CREATE).get(0).getSqlText().replaceAll("\\s+", " ");
	}
	private Index roundTrip(Index index) throws Exception {
		Table restored = SchemaUtils.readXml(new StringReader(index.getTable().asXml())); return restored.getIndexes().get("IX");
	}
	@Test void localAndGlobalAdvancedCompressionSurviveMetadataXmlAndSql() throws Exception {
		for (boolean local : new boolean[] {true, false}) {
			for (String mode : new String[] {"ADVANCED LOW", "ADVANCED HIGH"}) {
				Index index = index(local); read(mode, index.getPartitioning().getPartitions().get(0));
				String sql = create(roundTrip(index));
				assertTrue(sql.contains("TABLESPACE \"Data Space\" COMPRESS " + mode), sql);
				assertFalse(sql.contains("TABLESPACE ="), sql);
			}
		}
	}
	@Test void disabledPartitionOverridesCompressedIndex() throws Exception {
		Index index = index(true).setCompression(true).setCompressionType("ENABLED");
		index.getSpecifics().put("oracle.index.prefixLength", "1");
		read("DISABLED", index.getPartitioning().getPartitions().get(0));
		String sql = create(roundTrip(index));
		assertTrue(sql.contains("TABLESPACE \"Data Space\" NOCOMPRESS"), sql);
		assertTrue(sql.endsWith("COMPRESS 1"), sql);
	}
	@Test void subpartitionCompressionIsInheritedWithoutAnIllegalClause() throws Exception {
		Index index = index(true).setCompression(true).setCompressionType("ENABLED");
		Partition parent = index.getPartitioning().getPartitions().get(0);
		read("ENABLED", parent);
		SubPartition sub = new SubPartition("SP1").setTableSpaceName("USERS"); read("ENABLED", sub);
		parent.getSubPartitions().add(sub);
		String sql = create(roundTrip(index));
		assertTrue(sql.contains("TABLESPACE \"Data Space\" COMPRESS (SUBPARTITION SP1 TABLESPACE USERS)"), sql);
		read("DISABLED", sub);
		assertThrows(IllegalArgumentException.class, () -> create(index));
	}
	@Test void prefixCompressionNeedsACompressedIndexAndNullIsNotCompression() throws Exception {
		Index index = index(true); Partition partition = index.getPartitioning().getPartitions().get(0);
		read(null, partition); assertFalse(partition.isCompression()); assertNull(partition.getCompressionType());
		read("ENABLED", partition); assertThrows(IllegalArgumentException.class, () -> create(index));
		index.setCompression(true); assertTrue(create(index).contains("COMPRESS"));
	}
	@Test void unsupportedModesAndVersionBoundariesFail() throws Exception {
		Index index = index(true); Partition partition = index.getPartitioning().getPartitions().get(0);
		read("TYPO", partition); assertThrows(IllegalArgumentException.class, () -> create(index));
		read("ADVANCED HIGH", partition);
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle12cDialect.createSqlFactoryRegistry().createSql(index, SqlType.CREATE));
		index.getSpecifics().put("oracle.index.bitmap", true);
		assertThrows(IllegalArgumentException.class, () -> create(index));
	}
	@Test void hashPartitionCompressionMustMatchItsIndexDefault() throws Exception {
		Index index = index(true); index.getPartitioning().setPartitioningType(PartitioningType.Hash);
		Partition partition = index.getPartitioning().getPartitions().get(0);
		read("DISABLED", partition); assertFalse(create(index).contains("NOCOMPRESS"));
		read("ADVANCED LOW", partition); assertThrows(IllegalArgumentException.class, () -> create(index));
	}
	@Test void globalMaximumBoundaryAndTablespaceUseOracleSyntax() {
		Index index = index(false);
		index.getPartitioning().getPartitions().get(0).setHighValue("MAXVALUE");
		String sql = create(index);
		assertTrue(sql.contains("VALUES LESS THAN (MAXVALUE) TABLESPACE \"Data Space\""), sql);
		index.getPartitioning().setSubPartitioningType(PartitioningType.Hash);
		assertThrows(IllegalArgumentException.class, () -> create(index));
	}
}
