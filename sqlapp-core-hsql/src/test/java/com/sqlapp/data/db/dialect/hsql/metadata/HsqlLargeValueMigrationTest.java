/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.hsql.HsqlTableMigration;
import com.sqlapp.data.db.dialect.hsql.HsqlTableMigration.Access;
import com.sqlapp.data.db.dialect.hsql.HsqlTableMigration.Phase;
import com.sqlapp.data.schemas.Catalog;

class HsqlLargeValueMigrationTest {
	@Test
	void namedArrayStorageSupportsMigrationAndRecovery() throws Exception {
		for (boolean batch : java.util.List.of(false, true))
			for (boolean fail : java.util.List.of(false, true))
				try (var c = open("named-array-migration-" + batch + "-" + fail); var s = c.createStatement()) {
					s.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
					s.execute("CREATE DOMAIN APP.D AS VARCHAR(17) ARRAY[4] CONSTRAINT C CHECK(CARDINALITY(VALUE)<4)");
					s.execute("CREATE TYPE APP.M AS INTEGER ARRAY[4]");
					s.execute("CREATE TABLE APP.T(N INTEGER,A APP.D,B APP.M)");
					s.execute(
							"INSERT INTO APP.T VALUES(1,ARRAY[CAST('a' AS VARCHAR(17)),NULL,CAST('漢字' AS VARCHAR(17))],CAST(ARRAY[1,NULL,3] AS APP.M)),(2,NULL,NULL),(3,ARRAY[],CAST(ARRAY[] AS APP.M))");
					var source = read(c);
					var target = source.clone();
					var table = target.getSchemas().get("APP").getTables().get("T");
					table.getColumns().get("N").setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT);
					if (fail)
						table.getConstraints().add(new com.sqlapp.data.schemas.CheckConstraint("BAD", "N<0"));
					var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(c));
					if (batch) {
						var plan = migration.planAll(source, target);
						if (fail) {
							var failure = assertThrows(HsqlTableMigration.BatchFailure.class,
									() -> migration.execute(c, plan, Access.EXCLUSIVE));
							assertEquals(Phase.RESTORE, failure.getPhase());
							assertEquals(Phase.RECOVERED, migration.recover(c, failure, Access.EXCLUSIVE).phase());
						} else
							assertEquals(Phase.COMPLETE, migration.execute(c, plan, Access.EXCLUSIVE).phase());
					} else {
						var plan = migration.plan(source, target, table);
						if (fail) {
							var failure = assertThrows(HsqlTableMigration.Failure.class,
									() -> migration.execute(c, plan, Access.EXCLUSIVE));
							assertEquals(Phase.RESTORE, failure.getPhase());
							assertEquals(Phase.RECOVERED, migration.recover(c, failure, Access.EXCLUSIVE).phase());
						} else
							assertEquals(Phase.COMPLETE, migration.execute(c, plan, Access.EXCLUSIVE).phase());
					}
					try (var rs = s.executeQuery("SELECT A,B FROM APP.T ORDER BY N")) {
						assertTrue(rs.next());
						assertArrayEquals(new Object[] { "a", null, "漢字" }, (Object[]) rs.getArray(1).getArray());
						assertArrayEquals(new Object[] { 1, null, 3 }, (Object[]) rs.getArray(2).getArray());
						assertTrue(rs.next());
						assertNull(rs.getArray(1));
						assertNull(rs.getArray(2));
						assertTrue(rs.next());
						assertEquals(0, ((Object[]) rs.getArray(1).getArray()).length);
						assertFalse(rs.next());
					}
					assertThrows(java.sql.SQLException.class, () -> s
							.execute("INSERT INTO APP.T VALUES(4,ARRAY['a','b','c','d'],CAST(ARRAY[] AS APP.M))"));
				}
	}

	@Test
	void namedArraysConvertBetweenNamedDefinitionsWithoutLosingElements() throws Exception {
		for (String kind : java.util.List.of("DOMAIN", "TYPE"))
			try (var c = open("named-array-convert-" + kind); var s = c.createStatement()) {
				s.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
				s.execute("CREATE " + kind + " APP.D AS INTEGER ARRAY[4]");
				s.execute("CREATE " + kind + " APP.E AS BIGINT ARRAY[6]");
				s.execute("CREATE TABLE APP.T(A APP.D)");
				s.execute("INSERT INTO APP.T VALUES(CAST(ARRAY[1,NULL,3] AS APP.D)),(NULL)");
				var source = read(c);
				var target = source.clone();
				var table = target.getSchemas().get("APP").getTables().get("T");
				table.getColumns().get("A").setDataTypeName("\"APP\".\"E\"");
				var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(c));
				var plan = migration.plan(source, target, table);
				assertEquals(Phase.COMPLETE, migration.execute(c, plan, Access.EXCLUSIVE).phase());
				s.execute("DROP " + kind + " APP.D");
				try (var rs = s.executeQuery("SELECT A FROM APP.T WHERE A IS NOT NULL")) {
					assertTrue(rs.next());
					assertArrayEquals(new Object[] { 1L, null, 3L }, (Object[]) rs.getArray(1).getArray());
					assertFalse(rs.next());
				}
				try (var rs = s.executeQuery("SELECT COUNT(*) FROM " + plan.backupTable())) {
					assertTrue(rs.next());
					assertEquals(2, rs.getInt(1));
				}
			}
	}

	@Test
	void namedArrayCardinalityLossIsRejectedBeforeReplacement() throws Exception {
		for (String kind : java.util.List.of("DOMAIN", "TYPE"))
			try (var c = open("named-array-truncate-" + kind); var s = c.createStatement()) {
				s.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
				s.execute("CREATE " + kind + " APP.D AS INTEGER ARRAY[4]");
				s.execute("CREATE " + kind + " APP.E AS INTEGER ARRAY[2]");
				s.execute("CREATE TABLE APP.T(A APP.D)");
				s.execute("INSERT INTO APP.T VALUES(CAST(ARRAY[1,NULL,3] AS APP.D))");
				var source = read(c);
				var target = source.clone();
				var table = target.getSchemas().get("APP").getTables().get("T");
				table.getColumns().get("A").setDataTypeName("\"APP\".\"E\"");
				var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(c));
				var plan = migration.planAll(source, target);
				var failure = assertThrows(HsqlTableMigration.BatchFailure.class,
						() -> migration.execute(c, plan, Access.EXCLUSIVE));
				assertEquals(Phase.VALIDATE, failure.getPhase());
				try (var rs = s.executeQuery("SELECT A FROM APP.T")) {
					assertTrue(rs.next());
					assertArrayEquals(new Object[] { 1, null, 3 }, (Object[]) rs.getArray(1).getArray());
				}
			}
	}

	@Test
	void omittedLobLengthsKeepTheNativeEngineDefault() throws Exception {
		try (var c = open("lob-defaults"); var s = c.createStatement()) {
			s.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			s.execute("CREATE TABLE APP.NATIVE_DEFAULTS(B BLOB,C CLOB)");
			var table = new com.sqlapp.data.schemas.Table("MODELED_DEFAULTS").setSchemaName("APP");
			table.getColumns().add(
					new com.sqlapp.data.schemas.Column("B").setDataType(com.sqlapp.data.db.datatype.DataType.BLOB));
			table.getColumns().add(
					new com.sqlapp.data.schemas.Column("C").setDataType(com.sqlapp.data.db.datatype.DataType.CLOB));
			for (var op : DialectResolver.getInstance().getDialect(c).createSqlFactoryRegistry().createSql(table,
					com.sqlapp.data.db.sql.SqlType.CREATE))
				s.execute(op.getSqlText());
			var schema = read(c).getSchemas().get("APP");
			for (String column : java.util.List.of("B", "C"))
				assertEquals(schema.getTables().get("NATIVE_DEFAULTS").getColumns().get(column).getLength(),
						schema.getTables().get("MODELED_DEFAULTS").getColumns().get(column).getLength());
		}
	}

	@Test
	void arrayBackedNamedTypesKeepTheirColumnIdentityDuringRecreation() throws Exception {
		try (var c = open("named-array-recreation"); var s = c.createStatement()) {
			s.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			s.execute("CREATE DOMAIN APP.D AS INTEGER ARRAY[4] CONSTRAINT C CHECK(CARDINALITY(VALUE)<4)");
			s.execute("CREATE TYPE APP.M AS INTEGER ARRAY[4]");
			s.execute("CREATE TABLE APP.DT(A APP.D)");
			s.execute("CREATE TABLE APP.MT(A APP.M)");
			var schema = read(c).getSchemas().get("APP");
			assertEquals(com.sqlapp.data.db.datatype.DataType.DOMAIN,
					schema.getTables().get("DT").getColumns().get("A").getDataType());
			assertTrue(schema.getTables().get("MT").getColumns().get("A").getDataTypeName().contains("M"));
			var restored = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
			s.execute("DROP SCHEMA APP CASCADE");
			for (var op : DialectResolver.getInstance().getDialect(c).createSqlFactoryRegistry().createSql(restored,
					com.sqlapp.data.db.sql.SqlType.CREATE))
				s.execute(op.getSqlText());
			s.execute("INSERT INTO APP.DT VALUES(ARRAY[1,2])");
			s.execute("INSERT INTO APP.MT VALUES(CAST(ARRAY[1,2] AS APP.M))");
			assertThrows(java.sql.SQLException.class, () -> s.execute("INSERT INTO APP.DT VALUES(ARRAY[1,2,3,4])"));
		}
	}

	@Test
	void overlengthSourceBlobsAreBackedUpFullyAndRejectedBeforeReplacement() throws Exception {
		try (var c = open("overlength-source"); var s = c.createStatement()) {
			s.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			s.execute("CREATE TABLE APP.T(N INTEGER,C BLOB(10))");
			try (var p = c.prepareStatement("INSERT INTO APP.T VALUES(1,?)")) {
				p.setBinaryStream(1, new java.io.ByteArrayInputStream(new byte[20000]));
				p.executeUpdate();
			}
			try (var rs = s.executeQuery("SELECT OCTET_LENGTH(C) FROM APP.T")) {
				assertTrue(rs.next());
				assertEquals(20000, rs.getLong(1));
			}
			var source = read(c);
			var target = source.clone();
			var table = target.getSchemas().get("APP").getTables().get("T");
			table.getConstraints().add(new com.sqlapp.data.schemas.CheckConstraint("BAD", "N<0"));
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(c));
			var plan = migration.plan(source, target, table, HsqlTableMigration.ConversionPolicy.ALLOW_LOSSY);
			var failure = assertThrows(HsqlTableMigration.Failure.class,
					() -> migration.execute(c, plan, Access.EXCLUSIVE));
			assertEquals(Phase.VALIDATE, failure.getPhase());
			assertNotNull(failure.getDataFingerprint());
			try (var rs = s.executeQuery("SELECT OCTET_LENGTH(C) FROM " + plan.backupTable())) {
				assertTrue(rs.next());
				assertEquals(20000, rs.getLong(1));
			}
			try (var rs = s.executeQuery("SELECT OCTET_LENGTH(C) FROM APP.T")) {
				assertTrue(rs.next());
				assertEquals(20000, rs.getLong(1));
			}
		}
	}

	@Test
	void declaredLobLengthsAboveIntegerRangeAreRetained() throws Exception {
		try (var c = open("lob-long-length"); var s = c.createStatement()) {
			s.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			s.execute("CREATE TABLE APP.T(N INTEGER,B BLOB(3G),C CLOB(3G))");
			s.execute("INSERT INTO APP.T VALUES(1,X'AB','text')");
			var source = read(c);
			var target = source.clone();
			var table = target.getSchemas().get("APP").getTables().get("T");
			table.getColumns().get("N").setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT);
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(c));
			assertEquals(Phase.COMPLETE,
					migration.execute(c, migration.planAll(source, target), Access.EXCLUSIVE).phase());
			assertEquals(3L * 1024 * 1024 * 1024,
					read(c).getSchemas().get("APP").getTables().get("T").getColumns().get("B").getLength());
			try (var rs = s.executeQuery("SELECT OCTET_LENGTH(B),C FROM APP.T")) {
				assertTrue(rs.next());
				assertEquals(1, rs.getLong(1));
				assertEquals("text", rs.getString(2));
			}
		}
	}

	@Test
	void lobsAndArraysRoundTripWithNullEmptyDuplicateAndLargeValues() throws Exception {
		for (boolean batch : java.util.List.of(false, true))
			try (var c = open("roundtrip-" + batch); var s = c.createStatement()) {
				setup(c);
				var source = read(c);
				var target = source.clone();
				target.getSchemas().get("APP").getTables().get("T").getColumns().get("N")
						.setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT);
				var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(c));
				if (batch)
					assertEquals(Phase.COMPLETE,
							migration.execute(c, migration.planAll(source, target), Access.EXCLUSIVE).phase());
				else
					assertEquals(Phase.COMPLETE,
							migration.execute(c,
									migration.plan(source, target, target.getSchemas().get("APP").getTables().get("T")),
									Access.EXCLUSIVE).phase());
				try (var rs = s.executeQuery("SELECT B,C,A FROM APP.T WHERE N=1")) {
					int rows = 0;
					while (rs.next()) {
						rows++;
						assertEquals(20000, rs.getBlob(1).length());
						assertEquals("漢字😀".repeat(5000), rs.getClob(2).getSubString(1, 20000));
						assertArrayEquals(new Object[] { 1, null, 3 }, (Object[]) rs.getArray(3).getArray());
					}
					assertEquals(2, rows);
				}
				try (var rs = s.executeQuery("SELECT COUNT(*) FROM APP.T")) {
					assertTrue(rs.next());
					assertEquals(4, rs.getInt(1));
				}
			}
	}

	@Test
	void losslessRejectsArrayAndLobTruncationBeforeReplacement() throws Exception {
		for (String column : java.util.List.of("B", "C", "A"))
			try (var c = open("truncate-" + column)) {
				setup(c);
				var source = read(c);
				var target = source.clone();
				var table = target.getSchemas().get("APP").getTables().get("T");
				if (column.equals("A"))
					table.getColumns().get(column).setArrayDimensionUpperBound(2);
				else
					table.getColumns().get(column).setLength(10L);
				var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(c));
				var plan = migration.plan(source, target, table);
				var failure = assertThrows(HsqlTableMigration.Failure.class,
						() -> migration.execute(c, plan, Access.EXCLUSIVE));
				assertEquals(Phase.VALIDATE, failure.getPhase());
				assertNotNull(failure.getDataFingerprint());
				if (failure.getCause() instanceof java.sql.SQLException sql)
					assertFalse(sql.getSQLState().startsWith("42"), "Validation must execute supported SQL");
			}
	}

	@Test
	void failedRestoreRecoversLobsAndDetectsSameLengthBackupTampering() throws Exception {
		for (boolean tamper : java.util.List.of(false, true))
			try (var c = open("recover-" + tamper); var s = c.createStatement()) {
				setup(c);
				var source = read(c);
				var target = source.clone();
				var table = target.getSchemas().get("APP").getTables().get("T");
				table.getConstraints().add(new com.sqlapp.data.schemas.CheckConstraint("BAD", "N<0"));
				var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(c));
				var plan = migration.planAll(source, target);
				var failure = assertThrows(HsqlTableMigration.BatchFailure.class,
						() -> migration.execute(c, plan, Access.EXCLUSIVE));
				assertEquals(Phase.RESTORE, failure.getPhase());
				if (tamper) {
					s.execute(
							"UPDATE " + plan.members().getFirst().backupTable() + " SET C=REPEAT('x',20000) WHERE N=1");
					assertEquals(Phase.RECOVER, assertThrows(HsqlTableMigration.BatchFailure.class,
							() -> migration.recover(c, failure, Access.EXCLUSIVE)).getPhase());
				} else {
					String backup = plan.members().getFirst().backupTable();
					s.execute("CREATE TABLE APP.REORDERED AS (SELECT * FROM " + backup + " ORDER BY N DESC) WITH DATA");
					s.execute("DELETE FROM " + backup);
					s.execute("INSERT INTO " + backup + " SELECT * FROM APP.REORDERED");
					s.execute("DROP TABLE APP.REORDERED");
					assertEquals(Phase.RECOVERED, migration.recover(c, failure, Access.EXCLUSIVE).phase());
					try (var rs = s.executeQuery("SELECT SUM(OCTET_LENGTH(B)),SUM(CHARACTER_LENGTH(C)) FROM APP.T")) {
						assertTrue(rs.next());
						assertEquals(40000, rs.getLong(1));
						assertEquals(40000, rs.getLong(2));
					}
				}
			}
	}

	@Test
	void arrayMetadataRecreatesThroughXmlWithElementLengthAndCardinality() throws Exception {
		try (var c = open("array-metadata"); var s = c.createStatement()) {
			s.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			s.execute("CREATE TABLE APP.T(A VARCHAR(17) ARRAY[6],N INTEGER ARRAY[4])");
			var schema = read(c).getSchemas().get("APP");
			assertEquals(1, schema.getTables().get("T").getColumns().get("A").getArrayDimension());
			assertEquals(6, schema.getTables().get("T").getColumns().get("A").getArrayDimensionUpperBound());
			var restored = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
			s.execute("DROP SCHEMA APP CASCADE");
			for (var op : DialectResolver.getInstance().getDialect(c).createSqlFactoryRegistry().createSql(restored,
					com.sqlapp.data.db.sql.SqlType.CREATE))
				s.execute(op.getSqlText());
			s.execute("INSERT INTO APP.T VALUES(ARRAY['12345678901234567'],ARRAY[1,NULL,3])");
			assertThrows(java.sql.SQLException.class,
					() -> s.execute("INSERT INTO APP.T VALUES(ARRAY['123456789012345678'],ARRAY[1])"));
			assertThrows(java.sql.SQLException.class,
					() -> s.execute("INSERT INTO APP.T VALUES(ARRAY['x'],ARRAY[1,2,3,4,5])"));
		}
	}

	private Connection open(String name) throws Exception {
		return DriverManager.getConnection("jdbc:hsqldb:mem:large-values-" + name, "SA", "");
	}

	private void setup(Connection c) throws Exception {
		try (var s = c.createStatement()) {
			s.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			s.execute("CREATE TABLE APP.T(N INTEGER,B BLOB(100000),C CLOB(100000),A INTEGER ARRAY[4])");
			s.execute("INSERT INTO APP.T VALUES(2,NULL,NULL,NULL),(3,X'','',ARRAY[])");
		}
		try (var p = c.prepareStatement("INSERT INTO APP.T VALUES(1,?,?,ARRAY[1,NULL,3])")) {
			byte[] bytes = new byte[20000];
			for (int i = 0; i < bytes.length; i++)
				bytes[i] = (byte) i;
			for (int i = 0; i < 2; i++) {
				p.setBinaryStream(1, new java.io.ByteArrayInputStream(bytes));
				p.setCharacterStream(2, new java.io.StringReader("漢字😀".repeat(5000)));
				p.executeUpdate();
			}
		}
	}

	private Catalog read(Connection c) throws Exception {
		var reader = DialectResolver.getInstance().getDialect(c).getCatalogReader();
		reader.getSchemaReader().setSchemaName("APP");
		var catalog = new Catalog("PUBLIC");
		catalog.getSchemas().addAll(reader.getSchemaReader().getAllFull(c));
		catalog.getObjectPrivileges().addAll(reader.getObjectPrivilegeReader().getAll(c));
		catalog.getColumnPrivileges().addAll(reader.getColumnPrivilegeReader().getAll(c));
		catalog.getRoutinePrivileges().addAll(reader.getRoutinePrivilegeReader().getAll(c));
		return catalog;
	}
}
