/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class InformixTablePrivilegeFactoriesTest {
	private ObjectPrivilege table() {
		return new ObjectPrivilege().setSchemaName("owner").setObjectName("items").setPrivilege("SELECT")
				.setGranteeName("reader").setGrantorName("owner").setHierachy(false);
	}

	private ColumnPrivilege column() {
		return new ColumnPrivilege().setSchemaName("owner").setTableName("items").setColumnName("odd\"column")
				.setPrivilege("UPDATE").setGranteeName("reader").setGrantorName("owner");
	}

	private String sql(Object value, SqlType type) {
		return DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(value, type).get(0).getSqlText();
	}

	@Test
	void preservesGrantorGrantOptionAndRelationIdentity() {
		assertEquals("GRANT SELECT ON \"owner\".\"items\" TO \"reader\" WITH GRANT OPTION AS \"owner\"",
				sql(table().setGrantable(true), SqlType.CREATE));
		assertEquals("REVOKE SELECT ON \"owner\".\"items\" FROM \"reader\" AS \"owner\"", sql(table(), SqlType.DROP));
	}

	@Test
	void handlesAllExplicitTablePrivileges() {
		for (String privilege : new String[] { "SELECT", "UPDATE", "INSERT", "DELETE", "REFERENCES", "INDEX", "ALTER",
				"UNDER" })
			assertTrue(sql(table().setPrivilege(privilege.toLowerCase(java.util.Locale.ROOT)), SqlType.GRANT)
					.startsWith("GRANT " + privilege + " ON"));
	}

	@Test
	void grantsOnlySupportedColumnPrivilegesAndNeverBroadensARevoke() {
		for (String privilege : new String[] { "SELECT", "UPDATE", "REFERENCES" })
			assertEquals(
					"GRANT " + privilege + " (\"odd\"\"column\") ON \"owner\".\"items\" TO \"reader\" AS \"owner\"",
					sql(column().setPrivilege(privilege), SqlType.GRANT));
		assertThrows(IllegalArgumentException.class, () -> sql(column(), SqlType.REVOKE));
		assertThrows(IllegalArgumentException.class, () -> sql(column(), SqlType.DROP));
		assertThrows(IllegalArgumentException.class, () -> sql(column().setPrivilege("INSERT"), SqlType.GRANT));
		assertThrows(IllegalArgumentException.class, () -> sql(column().setColumnName(null), SqlType.GRANT));
	}

	@Test
	void supportsPublicAndRejectsUnsupportedGrantSemantics() {
		assertTrue(sql(table().setGranteeName("public").setGrantorName(null), SqlType.GRANT).endsWith(" TO PUBLIC"));
		assertThrows(IllegalArgumentException.class, () -> sql(table().setHierachy(true), SqlType.GRANT));
		assertThrows(IllegalArgumentException.class, () -> sql(table().setState(PrivilegeState.Deny), SqlType.GRANT));
		assertThrows(IllegalArgumentException.class,
				() -> sql(table().setPrivilege("SELECT; DROP TABLE items"), SqlType.GRANT));
		assertThrows(IllegalArgumentException.class, () -> sql(table().setSpecificName("routine"), SqlType.GRANT));
		assertThrows(IllegalArgumentException.class,
				() -> sql(table().setGrantable(true).setGrantee(new Role("r")), SqlType.GRANT));
		assertThrows(IllegalArgumentException.class,
				() -> sql(table().setGrantable(true).setGranteeName("public"), SqlType.GRANT));
	}

	@Test
	void preservesSqlAcrossXml() throws Exception {
		ObjectPrivilege restoredTable = SchemaUtils.readXml(new java.io.StringReader(table().asXml()));
		ColumnPrivilege restoredColumn = SchemaUtils.readXml(new java.io.StringReader(column().asXml()));
		assertEquals(sql(table(), SqlType.GRANT), sql(restoredTable, SqlType.GRANT));
		assertEquals(sql(column(), SqlType.GRANT), sql(restoredColumn, SqlType.GRANT));
	}
}
