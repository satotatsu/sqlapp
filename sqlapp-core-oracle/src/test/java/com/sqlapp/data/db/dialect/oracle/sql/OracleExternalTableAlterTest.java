package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.dialect.oracle.util.OracleTableDefinition;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class OracleExternalTableAlterTest {
	private Table table() {
		Table table = new Table("EXT").setSchemaName("APP");
		table.getColumns().add("ID", c -> c.setDataType(DataType.BIGINT));
		table.getSpecifics().put("oracle.table.external", true);
		var partitions = new Partitioning().setPartitioningType(PartitioningType.Range);
		partitions.getPartitioningColumns().add("ID");
		partitions.getPartitions().add(new Partition("P1").setHighValue("100"));
		partitions.getPartitions().add(new Partition("P2").setHighValue("200"));
		table.setPartitioning(partitions);
		return table;
	}

	private void rejected(Table before, Table after) {
		for (var dialect : List.of(DialectHolder.oracle12cDialect, DialectHolder.oracle26aiDialect)) {
			var registry = dialect.createSqlFactoryRegistry();
			var failure = assertThrows(IllegalArgumentException.class,
					() -> registry.getSqlFactory(before, SqlType.ALTER).createDiffSql(before.diff(after)));
			assertTrue(failure.getMessage().contains("external table"), failure.getMessage());
		}
	}

	@Test
	void externalPartitionsNeverUseHeapMaintenance() {
		Table before = table();
		Table dropped = before.clone();
		dropped.getPartitioning().getPartitions().remove(1);
		rejected(before, dropped);
		Table moved = before.clone();
		moved.getPartitioning().getPartitions().get(0).setTableSpaceName("DATA");
		rejected(before, moved);
		Table split = before.clone();
		split.getPartitioning().getPartitions().get(1).setHighValue("150");
		split.getPartitioning().getPartitions().add(new Partition("P3").setHighValue("200"));
		rejected(before, split);
	}

	@Test
	void removingIdentificationAndChangingColumnsStillRequireAnExplicitPlan() {
		Table before = table();
		Table after = before.clone();
		after.getSpecifics().clear();
		after.getColumns().add("EXTRA", c -> c.setDataType(DataType.BIGINT));
		rejected(before, after);
		rejected(after, before);
	}

	@Test
	void legacySchemaIdentificationAlsoRejectsChanges() {
		Schema schema = new Schema("APP");
		Table before = table();
		before.getSpecifics().clear();
		schema.getTables().add(before);
		schema.getExternalTables().add(new ExternalTable("EXT"));
		Table renamed = before.clone().setName("RENAMED");
		rejected(before, renamed);
	}

	@Test
	void unchangedExternalModelsProduceNoSqlAndCapturedDefinitionsAreValidated() {
		Table table = table();
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		assertTrue(registry.getSqlFactory(table, SqlType.ALTER).createDiffSql(table.diff(table.clone())).isEmpty());
		OracleTableDefinition.set(table,
				"CREATE TABLE APP.EXT (ID NUMBER) ORGANIZATION EXTERNAL (TYPE ORACLE_LOADER DEFAULT DIRECTORY DATA LOCATION ('a.csv'))");
		assertTrue(registry.getSqlFactory(table, SqlType.ALTER).createDiffSql(table.diff(table.clone())).isEmpty());
		table.setName("STALE");
		assertThrows(IllegalArgumentException.class,
				() -> registry.getSqlFactory(table, SqlType.ALTER).createDiffSql(table.diff(table.clone())));
	}

	@Test
	void capturedPartitionedExternalTableCannotBypassTheDefinitionGuardAfterXml() throws Exception {
		Table table = table();
		String ddl = "CREATE TABLE APP.EXT (ID NUMBER) ORGANIZATION EXTERNAL (TYPE ORACLE_LOADER "
				+ "DEFAULT DIRECTORY DATA) PARTITION BY RANGE(ID) (PARTITION P1 VALUES LESS THAN(100) LOCATION ('a.csv'), "
				+ "PARTITION P2 VALUES LESS THAN(200) LOCATION ('b.csv'))";
		OracleTableDefinition.set(table, ddl);
		Table before = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		Table after = before.clone();
		after.getPartitioning().getPartitions().get(0).setTableSpaceName("OTHER");
		rejected(before, after);
		assertEquals(ddl, OracleTableDefinition.get(before));
	}
}
