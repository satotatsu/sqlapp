/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.sqlserver.resolver.SqlServerDialectResolver;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerPartitionOptions;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

class SqlServerPartitionSchemeAlterTest {
	private PartitionScheme scheme() {
		return new PartitionScheme("Scheme Data").setPartitionFunctionName("Function Data").setTableSpaces("PRIMARY");
	}

	@Test
	void assignmentRemovalAndXmlRoundTripWorkAcrossVersions() throws Exception {
		for (int version : new int[] { 9, 10, 11, 12, 13, 14, 15, 16 }) {
			var registry = SqlServerDialectResolver.getInstance().getDialect(version, 0).createSqlFactoryRegistry();
			PartitionScheme original = scheme(), target = original.clone();
			SqlServerPartitionOptions.nextUsed(target, "File Group]");
			SqlFactory<PartitionScheme> alter = registry.getSqlFactory(target, SqlType.ALTER);
			var sql = alter.createDiffSql(original.diff(target));
			assertEquals(1, sql.size());
			assertTrue(
					sql.get(0).getSqlText().contains("ALTER PARTITION SCHEME [Scheme Data] NEXT USED [File Group]]]"));
			PartitionScheme restored = SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
			assertEquals("File Group]", SqlServerPartitionOptions.getNextUsed(restored));
			PartitionScheme cleared = target.clone();
			SqlServerPartitionOptions.clearNextUsed(cleared);
			assertTrue(alter.createDiffSql(target.diff(cleared)).get(0).getSqlText().strip().endsWith("NEXT USED"));
			assertTrue(alter.createDiffSql(cleared.diff(cleared.clone())).isEmpty());
			var create = registry.createSql(target, SqlType.CREATE);
			assertEquals(2, create.size());
			assertTrue(create.get(0).getSqlText().contains("[PRIMARY]"));
			assertTrue(create.get(1).getSqlText().contains("NEXT USED [File Group]]]"));
		}
	}

	@Test
	void nextUsedChangesPreserveRepeatedCatalogDestinations() {
		PartitionScheme original = scheme().setCatalogName("METADATA_READER_2017_TEST");
		original.getTableSpaces().clear();
		for (int i = 1; i <= 4; i++)
			original.getTableSpaces().add(new TableSpace("PRIMARY").setId(Integer.toString(i)));
		SqlServerPartitionOptions.nextUsed(original, "PRIMARY");
		PartitionScheme target = original.clone();
		assertEquals(original, target, () -> original.diff(target).toString());
		SqlServerPartitionOptions.clearNextUsed(target);
		var registry = SqlServerDialectResolver.getInstance().getDialect(14, 0).createSqlFactoryRegistry();
		SqlFactory<PartitionScheme> alter = registry.getSqlFactory(target, SqlType.ALTER);
		assertEquals(1, alter.createDiffSql(original.diff(target)).size());
	}

	@Test
	void unsupportedPlacementChangesAndAmbiguousRemovalAreRejected() {
		var registry = SqlServerDialectResolver.getInstance().getDialect(11, 0).createSqlFactoryRegistry();
		PartitionScheme original = scheme();
		SqlServerPartitionOptions.nextUsed(original, "PRIMARY");
		SqlFactory<PartitionScheme> alter = registry.getSqlFactory(original, SqlType.ALTER);
		PartitionScheme removed = original.clone();
		removed.getSpecifics().remove(SqlServerPartitionOptions.NEXT_USED);
		assertThrows(IllegalArgumentException.class, () -> alter.createDiffSql(original.diff(removed)));
		PartitionScheme changed = original.clone().setTableSpaces("Other");
		assertThrows(IllegalArgumentException.class, () -> alter.createDiffSql(original.diff(changed)));
		PartitionScheme reassigned = original.clone().setPartitionFunctionName("Other");
		assertThrows(IllegalArgumentException.class, () -> alter.createDiffSql(original.diff(reassigned)));
		assertThrows(IllegalArgumentException.class, () -> alter.createSql(scheme()));
		assertThrows(IllegalArgumentException.class, () -> SqlServerPartitionOptions.nextUsed(scheme(), " "));
	}
}
