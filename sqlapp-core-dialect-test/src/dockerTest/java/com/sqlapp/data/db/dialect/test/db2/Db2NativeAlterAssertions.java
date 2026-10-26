/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.db2;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.sql.*;
import java.util.*;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/** Executed only by the explicitly selected disposable-container tests. */
final class Db2NativeAlterAssertions {
	private Db2NativeAlterAssertions() {
	}

	static void verify(Connection connection) throws Exception {
		String schemaName = "SQLAPP_NATIVE_ALTER";
		var dialect = DialectResolver.getInstance().getDialect(connection);
		Deque<String> cleanup = new ArrayDeque<>();
		Throwable originalFailure = null;
		try (var statement = connection.createStatement()) {
			try {
				statement.execute("CREATE SCHEMA " + schemaName);
				cleanup.push("DROP SCHEMA " + schemaName + " RESTRICT");
				statement.execute(
						"CREATE SEQUENCE " + schemaName + ".S AS BIGINT START WITH 1 INCREMENT BY 1 CACHE 20 NO ORDER");
				cleanup.push("DROP SEQUENCE " + schemaName + ".S RESTRICT");
				statement.execute("CREATE TABLE " + schemaName + ".T (ID INTEGER)");
				cleanup.push("DROP TABLE " + schemaName + ".T");
				statement.execute("CREATE INDEX " + schemaName + ".I ON " + schemaName + ".T (ID) COMPRESS NO");
				cleanup.push("DROP INDEX " + schemaName + ".I");
				statement.execute("CREATE VIEW " + schemaName + ".V AS SELECT ID FROM " + schemaName + ".T");
				cleanup.push("DROP VIEW " + schemaName + ".V");
				statement.execute("CREATE ALIAS " + schemaName + ".A FOR TABLE " + schemaName + ".T");
				cleanup.push("DROP ALIAS " + schemaName + ".A FOR TABLE");
				statement.execute("CREATE TABLE " + schemaName + ".XT (DOC XML)");
				cleanup.push("DROP TABLE " + schemaName + ".XT");
				statement.execute("CREATE INDEX " + schemaName + ".XI ON " + schemaName
						+ ".XT(DOC) GENERATE KEY USING XMLPATTERN '/book/price' AS SQL DECIMAL(18,2) REJECT INVALID VALUES");
				cleanup.push("DROP INDEX " + schemaName + ".XI");
				statement.execute("INSERT INTO " + schemaName
						+ ".XT VALUES(XMLPARSE(DOCUMENT '<book><price>12.30</price></book>'))");
				statement.execute("CREATE MODULE " + schemaName + ".M");
				cleanup.push("DROP MODULE " + schemaName + ".M");
				statement.execute("ALTER MODULE " + schemaName + ".M PUBLISH CONDITION C FOR SQLSTATE '75001'");
				statement.execute("ALTER MODULE " + schemaName + ".M PUBLISH VARIABLE V INTEGER");
				statement.execute("ALTER MODULE " + schemaName
						+ ".M PUBLISH TYPE R AS ROW (ID INTEGER, LABEL VARCHAR(10 OCTETS))");
				statement.execute("ALTER MODULE " + schemaName + ".M PUBLISH VARIABLE RV R");
				statement.execute("ALTER MODULE " + schemaName + ".M PUBLISH TYPE RA AS R ARRAY[10]");
				statement.execute("ALTER MODULE " + schemaName + ".M PUBLISH TYPE N AS ROW (ITEMS RA)");
				statement.execute("ALTER MODULE " + schemaName + ".M PUBLISH VARIABLE NV N");
				statement.execute("ALTER MODULE " + schemaName
						+ ".M PUBLISH FUNCTION F(P INTEGER) RETURNS INTEGER SPECIFIC F_S LANGUAGE SQL RETURN P + 1");
				statement.execute("ALTER MODULE " + schemaName
						+ ".M ADD PROCEDURE P() SPECIFIC P_S LANGUAGE SQL BEGIN DECLARE X INTEGER; SET X=1; SET X=X+1; END");
				var reader = dialect.getCatalogReader().getSchemaReader();
				reader.setSchemaName(schemaName);
				Schema before = reader.getAllFull(connection).stream().filter(s -> schemaName.equals(s.getName()))
						.findFirst().orElseThrow();
				BigInteger first;
				try (var rows = statement.executeQuery("VALUES NEXT VALUE FOR " + schemaName + ".S")) {
					assertTrue(rows.next());
					first = rows.getBigDecimal(1).toBigIntegerExact();
				}
				Schema after = SchemaUtils.readXml(new java.io.StringReader(before.asXml()));
				after.getSequences().get("S").setIncrementBy(BigInteger.TWO).setCache(false).setOrder(true)
						.setRemarks("sequence changed");
				after.getTables().get("T").getIndexes().get("I").setCompression(true).setRemarks("index changed");
				after.getViews().get("V").setRemarks("view changed");
				after.getSynonyms().get("A").setRemarks("alias changed");
				SqlFactory<Schema> factory = dialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER);
				var operations = factory.createDiffSql(before.diff(after));
				assertFalse(operations.isEmpty());
				for (var operation : operations) {
					String sql = operation.getSqlText();
					assertFalse(sql.startsWith("DROP") || sql.startsWith("CREATE") || sql.contains("RESTART"), sql);
					statement.execute(sql);
				}
				Schema actual = reader.getAllFull(connection).stream().filter(s -> schemaName.equals(s.getName()))
						.findFirst().orElseThrow();
				assertEquals(BigInteger.TWO, actual.getSequences().get("S").getIncrementBy());
				assertFalse(actual.getSequences().get("S").isCache());
				assertTrue(actual.getSequences().get("S").isOrder());
				assertTrue(actual.getTables().get("T").getIndexes().get("I").isCompression());
				assertEquals("index changed", actual.getTables().get("T").getIndexes().get("I").getRemarks());
				assertEquals("sequence changed", actual.getSequences().get("S").getRemarks());
				assertEquals("view changed", actual.getViews().get("V").getRemarks());
				assertEquals("alias changed", actual.getSynonyms().get("A").getRemarks());
				assertEquals(1, actual.getTables().get("XT").getIndexes().size());
				Index xmlIndex = after.getTables().get("XT").getIndexes().get("XI");
				assertEquals("XVIL", xmlIndex.getSpecifics().get("INDEXTYPE"));
				assertEquals("/book/price", xmlIndex.getSpecifics().get("XML_PATTERN"));
				assertEquals("DECIMAL", xmlIndex.getSpecifics().get("XML_DATATYPE"));
				SqlFactory<Index> recreateXml = dialect.createSqlFactoryRegistry().getSqlFactory(xmlIndex,
						SqlType.CREATE);
				statement.execute("DROP INDEX " + schemaName + ".XI");
				for (var operation : recreateXml.createSql(xmlIndex))
					statement.execute(operation.getSqlText());
				Schema recreated = reader.getAllFull(connection).stream().filter(s -> schemaName.equals(s.getName()))
						.findFirst().orElseThrow();
				assertEquals(xmlIndex.getSpecifics().get("XML_PATTERN"),
						recreated.getTables().get("XT").getIndexes().get("XI").getSpecifics().get("XML_PATTERN"));
				try (var rows = statement.executeQuery("SELECT COUNT(*) FROM " + schemaName + ".XT")) {
					assertTrue(rows.next());
					assertEquals(1, rows.getInt(1));
				}
				com.sqlapp.data.schemas.Package module = after.getPackages().get("M");
				assertEquals(9, com.sqlapp.data.db.dialect.db2.Db2ModuleMembers.get(module).members().size());
				SqlFactory<com.sqlapp.data.schemas.Package> recreateModule = dialect.createSqlFactoryRegistry()
						.getSqlFactory(module, SqlType.CREATE);
				statement.execute("DROP MODULE " + schemaName + ".M");
				for (var operation : recreateModule.createSql(module))
					statement.execute(operation.getSqlText());
				Schema moduleActual = reader.getAllFull(connection).stream().filter(s -> schemaName.equals(s.getName()))
						.findFirst().orElseThrow();
				var rereadMembers = com.sqlapp.data.db.dialect.db2.Db2ModuleMembers
						.get(moduleActual.getPackages().get("M")).members();
				assertEquals(9, rereadMembers.size());
				var rereadRow = rereadMembers.stream().filter(member -> member.name().equals("R")).findFirst()
						.orElseThrow();
				assertEquals(List.of("ID", "LABEL"), com.sqlapp.data.db.dialect.db2.Db2ModuleMembers
						.getRowType(rereadRow).getColumns().stream().map(TypeColumn::getName).toList());
				try (var rows = statement.executeQuery("VALUES " + schemaName + ".M.F(41)")) {
					assertTrue(rows.next());
					assertEquals(42, rows.getInt(1));
				}
				try (var rows = statement.executeQuery("VALUES NEXT VALUE FOR " + schemaName + ".S")) {
					assertTrue(rows.next());
					assertTrue(rows.getBigDecimal(1).toBigIntegerExact().compareTo(first) > 0);
				}
			} catch (Exception | AssertionError failure) {
				originalFailure = failure;
				throw failure;
			} finally {
				SQLException cleanupFailure = null;
				for (String sql : cleanup)
					try {
						statement.execute(sql);
					} catch (SQLException failure) {
						if (originalFailure != null)
							originalFailure.addSuppressed(failure);
						else if (cleanupFailure == null)
							cleanupFailure = failure;
						else
							cleanupFailure.addSuppressed(failure);
					}
				if (cleanupFailure != null)
					throw cleanupFailure;
			}
		}
	}
}
