/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;

class Db2RowPermissionsTest {
	@Test
	void diffRetainsExplicitDisableAndRejectsAmbiguousRemoval() {
		Schema original = new Schema("APP"), target = new Schema("APP");
		Db2RowPermissions.set(original, List.of(permission("APP")),
				List.of(new Db2RowPermissions.AccessControl("APP", "T", true, false)));
		assertThrows(IllegalArgumentException.class, () -> Db2RowPermissions.diff(original, target));
		Db2RowPermissions.set(target, List.of(),
				List.of(new Db2RowPermissions.AccessControl("APP", "T", false, false)));
		var changes = Db2RowPermissions.diff(original, target);
		assertEquals(List.of("DROP PERMISSION \"APP\".\"P\"\"1\""), changes.beforeSql());
		assertEquals(List.of("ALTER TABLE \"APP\".\"T\" DEACTIVATE ROW ACCESS CONTROL"), changes.afterSql());
		assertTrue(Db2RowPermissions.diff(original, original.clone()).beforeSql().isEmpty());
		assertTrue(Db2RowPermissions.diff(original, original.clone()).afterSql().isEmpty());
	}

	private Db2RowPermissions.Permission permission(String schema) {
		return new Db2RowPermissions.Permission(schema, "P\"1", "TARGET", "T", "\"r\".ID > 0", "r", true, true,
				"RESOLVE", "\"SYSIBM\", \"a,b\", \"a\"\"b\"", "owner's rule");
	}

	@Test
	void retainsPermissionsAndActivationThroughXmlAndClone() throws Exception {
		Schema schema = new Schema("APP");
		Db2RowPermissions.set(schema, List.of(permission("APP")),
				List.of(new Db2RowPermissions.AccessControl("APP", "T", true, true)));
		Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
		assertEquals(Db2RowPermissions.get(schema), Db2RowPermissions.get(restored));
		assertEquals(Db2RowPermissions.get(schema), Db2RowPermissions.get(schema.clone()));
		var sql = Db2RowPermissions.createSql(restored);
		assertEquals("SET CURRENT SCHEMA = 'RESOLVE'", sql.get(0));
		assertEquals("SET CURRENT PATH = \"SYSIBM\", \"a,b\", \"a\"\"b\"", sql.get(1));
		assertTrue(sql.get(2).contains("\"APP\".\"P\"\"1\" ON \"TARGET\".\"T\" AS \"r\" FOR ROWS WHERE \"r\".ID > 0"));
		assertTrue(sql.get(2).endsWith("ENFORCED FOR ALL ACCESS ENABLE"));
		assertTrue(sql.get(3).endsWith("IS 'owner''s rule'"));
		assertEquals("ALTER TABLE \"APP\".\"T\" ACTIVATE ROW ACCESS CONTROL", sql.get(4));
		assertEquals("ALTER TABLE \"APP\".\"T\" ACTIVATE COLUMN ACCESS CONTROL", sql.get(5));
	}

	@Test
	void disabledPermissionDoesNotActivateItsTable() {
		Schema schema = new Schema("APP");
		var permission = new Db2RowPermissions.Permission("APP", "P", "APP", "T", "ID > 0", null, false, true, null,
				null, null);
		Db2RowPermissions.set(schema, List.of(permission),
				List.of(new Db2RowPermissions.AccessControl("APP", "T", false, false)));
		assertEquals(1, Db2RowPermissions.createSql(schema).size());
		assertTrue(Db2RowPermissions.createSql(schema).getFirst().endsWith("DISABLE"));
	}

	@Test
	void rejectsStaleScopeDuplicateIdentityAndInvalidSource() {
		Schema schema = new Schema("APP");
		assertThrows(IllegalArgumentException.class,
				() -> Db2RowPermissions.set(schema, List.of(permission("OTHER")), List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> Db2RowPermissions.set(schema, List.of(permission("APP"), permission("APP")), List.of()));
		Db2RowPermissions.set(schema, List.of(permission("APP")), List.of());
		schema.setName("RENAMED");
		assertThrows(IllegalArgumentException.class, () -> Db2RowPermissions.get(schema));
		var invalid = new Db2RowPermissions.Permission("APP", "P", "APP", "T", "ID>0", null, true, false, null, null,
				null);
		Schema target = new Schema("APP");
		Db2RowPermissions.set(target, List.of(invalid), List.of());
		assertThrows(IllegalArgumentException.class, () -> Db2RowPermissions.createSql(target));
	}

	@Test
	void rejectsUnknownPayloadFieldsMissingFlagsAndUnsafePaths() {
		Schema schema = new Schema("APP");
		schema.getSpecifics().put(Db2RowPermissions.SPECIFICS_KEY,
				"{\"format\":1,\"permissions\":[],\"tables\":[],\"typo\":1}");
		assertThrows(Exception.class, () -> Db2RowPermissions.get(schema));
		schema.getSpecifics().put(Db2RowPermissions.SPECIFICS_KEY,
				"{\"format\":1,\"permissions\":[],\"tables\":[{\"tableSchemaName\":\"APP\",\"tableName\":\"T\"}]}");
		assertThrows(Exception.class, () -> Db2RowPermissions.get(schema));
		for (String path : List.of("SYSIBM; DROP TABLE T", "\"unterminated", "SYSIBM,", "\"A\" unexpected")) {
			assertThrows(IllegalArgumentException.class, () -> Db2CreationContext.sql("APP", path));
		}
		Db2RowPermissions.set(schema, List.of(), List.of());
		assertFalse(schema.getSpecifics().containsKey(Db2RowPermissions.SPECIFICS_KEY));
	}
}
