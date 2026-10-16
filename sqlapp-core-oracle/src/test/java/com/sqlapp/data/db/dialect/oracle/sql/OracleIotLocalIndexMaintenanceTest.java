/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;

class OracleIotLocalIndexMaintenanceTest {
	private Table source() {
		Table table = new Table("T").setSchemaName("APP"); table.getColumns().add("ID"); table.getSpecifics().put("oracle.table.organization", "INDEX");
		Partitioning info = new Partitioning().setPartitioningType(PartitioningType.Range); info.getPartitioningColumns().add("ID");
		info.getPartitions().add(new Partition("P1").setHighValue("100")); info.getPartitions().add(new Partition("P2").setHighValue("200")); table.setPartitioning(info);
		Index index = new Index("IX"); table.getIndexes().add(index); index.getColumns().add("ID"); index.getSpecifics().put("oracle.index.locality", "LOCAL"); index.setPartitioning(info.clone());
		for (Partition part : index.getPartitioning().getPartitions()) { part.setName("I" + part.getName()); part.getSpecifics().put("oracle.index.status", "USABLE"); }
		return table.clone();
	}
	private Table split(Table before) {
		Table after = before.clone(); after.getPartitioning().getPartitions().get(1).setName("LOW"); after.getPartitioning().getPartitions().get(1).setHighValue("150");
		after.getPartitioning().getPartitions().add(new Partition("HIGH").setHighValue("200"));
		for (Index index : after.getIndexes()) if (index.getPartitioning() != null) {
			index.getPartitioning().getPartitions().remove(1);
			for (String name : List.of("ILOW", "IHIGH")) {
				Partition part = new Partition(name).setHighValue(name.equals("ILOW") ? "150" : "200").setTableSpaceName("Index Space"); part.getSpecifics().put("oracle.index.status", "USABLE"); index.getPartitioning().getPartitions().add(part);
			}
		}
		return after;
	}
	private List<String> sql(Table before, Table after) {
		return sql(before, after, DialectHolder.oracle26aiDialect);
	}
	private List<String> sql(Table before, Table after, Dialect dialect) {
		var factory = dialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER); factory.getOptions().setDecorateSchemaName(true);
		return factory.createDiffSql(before.diff(after)).stream().map(operation -> operation.getSqlText()).toList();
	}
	@Test void splitUsesNativeDdlAndOneBulkNameResolutionBlock() {
		Table before = source(), after = split(before); var operations = sql(before, after);
		assertEquals(2, operations.size()); assertEquals("ALTER TABLE APP.T SPLIT PARTITION P2 AT (150) INTO (PARTITION LOW, PARTITION HIGH)", operations.get(0).replaceAll("\\s+", " "));
		String block = operations.get(1); assertEquals(1, block.split("BULK COLLECT INTO", -1).length - 1);
		assertTrue(block.contains("2 position_no, 'ILOW' target_name")); assertTrue(block.contains("3 position_no, 'IHIGH' target_name"));
		assertTrue(block.contains("segments.COUNT <> 2")); assertTrue(block.contains(" REBUILD PARTITION ")); assertFalse(block.contains("UPDATE INDEXES"));
		assertEquals(operations, sql(before, after)); assertEquals(2, before.getIndexes().get(0).getPartitioning().getPartitions().size());
	}
	@Test void mergeResolvesTheNewNameRatherThanGuessingIt() {
		Table target = source(), before = split(target); var operations = sql(before, target);
		assertEquals(2, operations.size()); assertTrue(operations.get(0).contains("MERGE PARTITIONS LOW, HIGH INTO PARTITION P2"));
		assertTrue(operations.get(1).contains("2 position_no, 'IP2' target_name")); assertTrue(operations.get(1).contains("segments.COUNT <> 1"));
		assertFalse(operations.toString().contains("DROP"));
	}
	@Test void multipleLocalIndexesShareOneCatalogResolution() {
		Table before = source(); Index extra = before.getIndexes().get(0).clone(); extra.setName("IX2"); before.getIndexes().add(extra);
		Index global = new Index("GX"); global.getColumns().add("ID"); before.getIndexes().add(global);
		var operations = sql(before, split(before)); assertEquals(2, operations.size());
		assertTrue(operations.get(1).contains("segments.COUNT <> 4")); assertEquals(1, operations.get(1).split("BULK COLLECT INTO", -1).length - 1); assertFalse(operations.get(1).contains("'GX' index_name"));
	}
	@Test void unusableTargetsWithTablespaceChangesAreMovedThenMarkedUnusable() {
		Table before = source(), after = split(before); after.getIndexes().get(0).getPartitioning().getPartitions().get(1).getSpecifics().put("oracle.index.status", "UNUSABLE");
		String block = sql(before, after).get(1); assertTrue(block.contains("'Index Space' target_space, 'UNUSABLE' target_state"));
		assertTrue(block.contains("IF segments(i).rebuild_required = 1 THEN")); assertTrue(block.contains(" MODIFY PARTITION "));
	}
	@Test void renamedPartitionsCanSwapThroughCollisionCheckedTemporaryNames() {
		Table before = source(), after = split(before); var parts = after.getIndexes().get(0).getPartitioning().getPartitions();
		parts.get(1).setName("IP2"); String block = sql(before, after).get(1);
		assertTrue(block.contains("SQLAPP_IOT_")); assertTrue(block.contains("x.PARTITION_NAME=r.temp_name")); assertTrue(block.contains("a.position_no=x.PARTITION_POSITION"));
		assertTrue(block.indexOf("names or count conflict") < block.indexOf("EXECUTE IMMEDIATE"));
	}
	@Test void identifiersAndLiteralsSurviveXmlWithoutInjection() throws Exception {
		Table before = source(); before.getIndexes().get(0).setName("Index'Name"); Table after = split(before);
		after.getIndexes().get(0).getPartitioning().getPartitions().get(1).setName("Part\"Name");
		Table restoredBefore = SchemaUtils.readXml(new java.io.StringReader(before.asXml())), restoredAfter = SchemaUtils.readXml(new java.io.StringReader(after.asXml()));
		String block = sql(restoredBefore, restoredAfter).get(1);
		assertTrue(block.contains("'Index''Name' index_name")); assertTrue(block.contains("'Part\"Name' target_name")); assertTrue(block.contains("REPLACE(p_name, '\"', '\"\"')"));
	}
	@Test void compressedAndUnmappedSegmentChangesFailBeforeReturningSql() {
		Table before = source(), after = split(before); after.getIndexes().get(0).getPartitioning().getPartitions().get(1).setCompression(true);
		assertThrows(IllegalArgumentException.class, () -> sql(before, after));
		Table wrong = split(before); wrong.getIndexes().get(0).getPartitioning().getPartitions().get(1).setHighValue("175"); assertThrows(IllegalArgumentException.class, () -> sql(before, wrong));
		Table untouched = split(before); untouched.getIndexes().get(0).getPartitioning().getPartitions().get(0).setTableSpaceName("OTHER"); assertThrows(IllegalArgumentException.class, () -> sql(before, untouched));
	}
	@Test void missingStateAndUnsupportedPhysicalAttributesAreRejected() {
		Table before = source(), after = split(before); after.getIndexes().get(0).getPartitioning().getPartitions().get(1).getSpecifics().remove("oracle.index.status"); assertThrows(IllegalArgumentException.class, () -> sql(before, after));
		Table physical = split(before); physical.getIndexes().get(0).getPartitioning().getPartitions().get(1).getSpecifics().put("PCT_FREE", "15"); assertThrows(IllegalArgumentException.class, () -> sql(before, physical));
	}
	@Test void currentSchemaAndIdentifierQuotingOptionsAreResolvedExplicitly() {
		Table before = source(); before.setSchemaName(null); before.getIndexes().get(0).setSchemaName(null); Table after = split(before);
		after.getIndexes().get(0).getPartitioning().getPartitions().get(1).setName("ilow");
		var factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER); factory.getOptions().setQuateObjectName(false);
		String block = factory.createDiffSql(before.diff(after)).get(1).getSqlText();
		assertTrue(block.contains("SYS_CONTEXT('USERENV','CURRENT_SCHEMA') owner_name")); assertTrue(block.contains("'ILOW' target_name"));
	}
	@Test void duplicateTargetNamesAndLocalRootChangesAreRejected() {
		Table before = source(), after = split(before); after.getIndexes().get(0).getPartitioning().getPartitions().get(1).setName("IP1");
		assertThrows(IllegalArgumentException.class, () -> sql(before, after));
		Table changed = split(before); changed.getIndexes().get(0).getSpecifics().put("oracle.index.visibility", "INVISIBLE"); assertThrows(IllegalArgumentException.class, () -> sql(before, changed));
	}
	@Test void generatedScriptRoundTripsAsTwoExecutableStatements() {
		Table before = source(); before.getIndexes().get(0).setName("Index'Name"); Table after = split(before);
		after.getIndexes().get(0).getPartitioning().getPartitions().get(1).setTableSpaceName("Index'\n/\nSpace");
		var factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER);
		var operations = factory.createDiffSql(before.diff(after)); assertEquals("/", operations.get(1).getTerminator());
		String script = operations.get(0).getSqlText() + ";\n" + operations.get(1).getSqlText() + "\n/\n";
		var parsed = DialectHolder.oracle26aiDialect.createSqlSplitter().parse(script);
		assertEquals(2, parsed.size()); assertEquals(operations.get(1).getSqlText(), parsed.get(1).getText());
	}
	@Test void templateMarkersInsideIdentifiersRemainLiteralData() {
		Table before = source(); before.getIndexes().get(0).setName("IDX$COUNT"); Table after = split(before);
		after.getIndexes().get(0).getPartitioning().getPartitions().get(1).setName("PART$REQUESTS");
		String block = sql(before, after).get(1); assertTrue(block.contains("'IDX$COUNT' index_name")); assertTrue(block.contains("'PART$REQUESTS' target_name"));
	}
	@Test void omittedSegmentTablespaceUsesItsModeledIndexDefault() {
		Table before = source(); before.getIndexes().get(0).setTableSpaceName("INDEX_DEFAULT"); Table after = split(before);
		after.getIndexes().get(0).getPartitioning().getPartitions().get(1).setTableSpaceName(null);
		assertTrue(sql(before, after).get(1).contains("'INDEX_DEFAULT' target_space"));
	}
	@Test void prefixCompressionAndExplicitNocompressUseExistingCompressionRules() {
		Table before = source(); before.getIndexes().get(0).setCompression(true).setCompressionType("ENABLED"); Table after = split(before);
		after.getIndexes().get(0).getPartitioning().getPartitions().get(1).setCompressionType("DISABLED");
		String block = sql(before, after, DialectHolder.oracle12cDialect).get(1);
		assertTrue(block.contains("' NOCOMPRESS' compression_clause")); assertTrue(block.contains("' COMPRESS' compression_clause"));
		assertTrue(block.contains("command := command || segments(i).compression_clause"));
	}
	@Test void advancedCompressionModesHaveDialectBoundaries() {
		for (String mode : List.of("ADVANCED LOW", "ADVANCED HIGH")) {
			Table before = source(); before.getIndexes().get(0).setCompression(true).setCompressionType(mode); Table after = split(before);
			assertTrue(sql(before, after, DialectHolder.oracle18cDialect).get(1).contains("' COMPRESS " + mode + "' compression_clause"));
			if (mode.equals("ADVANCED HIGH")) assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle12cDialect));
			else assertEquals(2, sql(before, after, DialectHolder.oracle12cDialect).size());
		}
	}
	@Test void unusableCompressionOverridesAreRebuiltBeforeStateChanges() {
		Table before = source(); before.getIndexes().get(0).setCompression(true).setCompressionType("ENABLED"); Table after = split(before);
		Partition part = after.getIndexes().get(0).getPartitioning().getPartitions().get(1); part.setTableSpaceName(null); part.setCompressionType("DISABLED"); part.getSpecifics().put("oracle.index.status", "UNUSABLE");
		assertTrue(sql(before, after).get(1).contains("'UNUSABLE' target_state, ' NOCOMPRESS' compression_clause, 1 rebuild_required"));
	}
	@Test void advancedCompressionCannotBeAddedToAnUncompressedRoot() {
		Table before = source(), after = split(before); Partition part = after.getIndexes().get(0).getPartitioning().getPartitions().get(1);
		part.setCompression(true).setCompressionType("ADVANCED LOW"); assertThrows(IllegalArgumentException.class, () -> sql(before, after));
	}
	@Test void invalidRootPrefixCannotPassThroughPartitionRebuilds() {
		Table before = source(); before.getIndexes().get(0).setCompression(true).setCompressionType("ENABLED"); before.getIndexes().get(0).getSpecifics().put("oracle.index.prefixLength", "2");
		Table after = split(before); assertThrows(IllegalArgumentException.class, () -> sql(before, after));
	}
}
