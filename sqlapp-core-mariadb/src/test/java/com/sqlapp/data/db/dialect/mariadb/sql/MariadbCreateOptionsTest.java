/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class MariadbCreateOptionsTest {
	private String sql(String options) throws Exception {
		Table table = new Table("options_test");
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		table.getSpecifics().put("CREATE_OPTIONS", options);
		Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		return DialectResolver.getInstance().getDialect("MariaDB", 11, 4, 0).createSqlFactoryRegistry()
				.createSql(restored, SqlType.CREATE).get(0).getSqlText();
	}

	@Test
	void removesOnlyUnquotedCatalogFlagAndRetainsLiteralValues() throws Exception {
		for (String literal : new String[] { "'a partitioned b'", "'a '' partitioned '' b'", "\"a partitioned b\"",
				"'a \\' partitioned b'", "'a $1 partitioned b'" }) {
			String ddl = sql("PARTITIONED CONNECTION=" + literal + " partitioned STATS_PERSISTENT=1");
			assertTrue(ddl.contains("CONNECTION=" + literal), ddl);
			assertTrue(ddl.contains("STATS_PERSISTENT=1"), ddl);
			assertFalse(ddl.contains("PARTITIONED CONNECTION"), ddl);
			assertFalse(ddl.contains(" partitioned STATS"), ddl);
		}
	}

	@Test
	void leavesOptionNamesContainingPartitionedAndEmptyOptionsIntact() throws Exception {
		assertTrue(sql("CONNECTION='partitioned' PAGE_COMPRESSED=1").contains("CONNECTION='partitioned'"));
		assertFalse(sql("partitioned").toLowerCase(java.util.Locale.ROOT).contains("partitioned"));
		assertFalse(sql("  ").contains("CREATE_OPTIONS"));
	}
}
