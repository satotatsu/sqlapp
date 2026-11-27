/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;

class MariadbSequenceTypeSqlTest {
	private String sql(int major, int minor, Sequence sequence) {
		return DialectResolver.getInstance().getDialect("MariaDB", major, minor, 0).createSqlFactoryRegistry()
				.createSql(sequence, SqlType.CREATE).get(0).getSqlText();
	}

	@Test
	void legacyBigintOmitsUnsupportedAsClause() {
		for (int[] version : new int[][] { { 10, 3 }, { 10, 5 }, { 11, 4 } }) {
			assertFalse(sql(version[0], version[1], new Sequence("seq").setDataType(DataType.BIGINT)).contains(" AS "));
			assertThrows(IllegalArgumentException.class,
					() -> sql(version[0], version[1], new Sequence("seq").setDataType(DataType.INT)));
		}
	}

	@Test
	void engineAndCommentOptionsArePreservedAndInvalidEngineIsRejected() {
		for (int[] version : new int[][] { { 10, 3 }, { 10, 11 }, { 11, 3 }, { 11, 5 }, { 12, 1 } }) {
			Sequence sequence = new Sequence("seq");
			sequence.getSpecifics().put("ENGINE", "Aria");
			sequence.setRemarks("owner's sequence");
			String ddl = sql(version[0], version[1], sequence);
			assertTrue(ddl.contains("ENGINE =Aria"), ddl);
			assertTrue(ddl.contains("owner''s sequence"), ddl);
			for (String invalid : new String[] { "", "InnoDB extra", "InnoDB; SELECT 1", "a\nb" }) {
				sequence.getSpecifics().put("ENGINE", invalid);
				assertThrows(IllegalArgumentException.class, () -> sql(version[0], version[1], sequence));
			}
		}
	}

	@Test
	void cacheSettingsSurviveXmlAndRejectContradictions() throws Exception {
		for (int[] version : new int[][] { { 10, 3 }, { 11, 4 }, { 11, 5 }, { 12, 1 } }) {
			Sequence disabled = new Sequence("seq").setCache(false);
			Sequence restored = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(disabled.asXml()));
			assertTrue(sql(version[0], version[1], restored).contains("NOCACHE"));
			assertTrue(sql(version[0], version[1], new Sequence("seq").setCacheSize(0)).contains("NOCACHE"));
			assertTrue(sql(version[0], version[1], new Sequence("seq").setCacheSize(50)).contains("CACHE 50"));
			assertFalse(sql(version[0], version[1], new Sequence("seq")).contains("CACHE"));
			assertThrows(IllegalArgumentException.class,
					() -> sql(version[0], version[1], new Sequence("seq").setCacheSize(-1)));
			assertThrows(IllegalArgumentException.class,
					() -> sql(version[0], version[1], new Sequence("seq").setCache(false).setCacheSize(50)));
		}
	}

	@Test
	void modernIntegerTypesRetainSignednessWithoutCatalogPrecision() {
		for (int[] version : new int[][] { { 11, 5 }, { 11, 8 }, { 12, 1 } }) {
			String ddl = sql(version[0], version[1],
					new Sequence("seq").setDataType(DataType.UBIGINT).setPrecision(64));
			assertTrue(ddl.contains(" AS BIGINT UNSIGNED"), ddl);
			assertFalse(ddl.contains("BIGINT("), ddl);
			assertThrows(IllegalArgumentException.class,
					() -> sql(version[0], version[1], new Sequence("seq").setDataType(DataType.VARCHAR)));
		}
	}
}
