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
				statement.execute("ALTER MODULE " + schemaName
						+ ".M PUBLISH VARIABLE V INTEGER DEFAULT (1 + 2 -- literal default\n)");
				statement.execute("ALTER MODULE " + schemaName
						+ ".M PUBLISH TYPE R AS ROW (ID INTEGER, LABEL VARCHAR(10 OCTETS))");
				statement.execute("ALTER MODULE " + schemaName
						+ ".M PUBLISH FUNCTION FROW(P R) RETURNS INTEGER SPECIFIC FROW_S LANGUAGE SQL RETURN P.ID");
				statement.execute("ALTER MODULE " + schemaName + ".M PUBLISH VARIABLE RV R");
				statement.execute("ALTER MODULE " + schemaName + ".M PUBLISH TYPE RA AS R ARRAY[10]");
				statement.execute("ALTER MODULE " + schemaName + ".M PUBLISH TYPE N AS ROW (ITEMS RA)");
				statement.execute("ALTER MODULE " + schemaName + ".M PUBLISH VARIABLE NV N");
				statement.execute("ALTER MODULE " + schemaName + ".M PUBLISH TYPE CW AS CURSOR");
				statement.execute("ALTER MODULE " + schemaName + ".M PUBLISH TYPE CS AS R CURSOR");
				statement.execute("ALTER MODULE " + schemaName + ".M PUBLISH VARIABLE CV CS");
				statement.execute("ALTER MODULE " + schemaName
						+ ".M PUBLISH FUNCTION F(P INTEGER) RETURNS INTEGER SPECIFIC F_S LANGUAGE SQL RETURN P + 1");
				statement.execute("ALTER MODULE " + schemaName
						+ ".M ADD PROCEDURE P() SPECIFIC P_S LANGUAGE SQL BEGIN DECLARE X INTEGER; SET X=1; SET X=X+1; END");
				statement.execute("ALTER MODULE " + schemaName
						+ ".M PUBLISH FUNCTION FP(P INTEGER) RETURNS INTEGER SPECIFIC FP_S");
				statement.execute("ALTER MODULE " + schemaName
						+ ".M PUBLISH PROCEDURE PP(IN P INTEGER) SPECIFIC PP_S LANGUAGE SQL DYNAMIC RESULT SETS 1 CALLED ON NULL INPUT COMMIT ON RETURN NO");
				statement.execute("ALTER MODULE " + schemaName
						+ ".M PUBLISH PROCEDURE PQ(OUT N INTEGER) SPECIFIC PQ_S LANGUAGE SQL DYNAMIC RESULT SETS 0 CALLED ON NULL INPUT COMMIT ON RETURN NO BEGIN SET N=7; END");
				statement.execute("ALTER MODULE " + schemaName
						+ ".M PUBLISH FUNCTION FD(N INTEGER DEFAULT 1) RETURNS INTEGER SPECIFIC FD_S LANGUAGE SQL RETURN N");
				statement.execute("ALTER MODULE " + schemaName
						+ ".M PUBLISH FUNCTION FA(N ANCHOR DATA TYPE TO V) RETURNS INTEGER SPECIFIC FA_S LANGUAGE SQL RETURN N");
				for (String name : List.of("RF", "RG"))
					statement.execute("ALTER MODULE " + schemaName + ".M PUBLISH FUNCTION " + name
							+ "(N INTEGER) RETURNS INTEGER SPECIFIC " + name
							+ "_S LANGUAGE SQL DETERMINISTIC NO EXTERNAL ACTION CONTAINS SQL");
				statement.execute("ALTER MODULE " + schemaName + ".M PUBLISH VARIABLE RD INTEGER DEFAULT (RF(4))");
				for (String name : List.of("RF", "RG"))
					statement.execute("ALTER MODULE " + schemaName + ".M ADD FUNCTION " + name
							+ "(N INTEGER) RETURNS INTEGER SPECIFIC " + name
							+ "_S LANGUAGE SQL DETERMINISTIC NO EXTERNAL ACTION CONTAINS SQL BEGIN "
							+ "IF N=0 THEN RETURN 0; END IF; RETURN " + (name.equals("RF") ? "RG" : "RF")
							+ "(N-1)+1; END");
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
				var originalMembers = com.sqlapp.data.db.dialect.db2.Db2ModuleMembers.get(module).members();
				var memberSnapshot = com.sqlapp.data.db.dialect.db2.Db2ModuleMembers.get(module);
				assertTrue(memberSnapshot.orderKnown(),
						originalMembers
								.stream().map(
										member -> member.kind() + ":" + member.name() + ":" + member.userDefined() + ":"
												+ member.attributes().get("MEMBER_CREATE_TIME") + ":"
												+ com.sqlapp.data.db.dialect.db2.Db2ModuleMembers
														.generatedTypeOwner(memberSnapshot, member))
								.toList().toString());
				assertEquals(21, originalMembers.stream()
						.filter(com.sqlapp.data.db.dialect.db2.Db2ModuleMembers.Member::userDefined).count());
				for (String routine : List.of("RF", "RG", "PQ"))
					assertEquals("Y", originalMembers.stream().filter(member -> member.name().equals(routine))
							.findFirst().orElseThrow().attributes().get("ROUTINE_DECLARATION_INDEPENDENT"));
				for (String routine : List.of("FROW", "FD"))
					assertEquals("N", originalMembers.stream().filter(member -> member.name().equals(routine))
							.findFirst().orElseThrow().attributes().get("ROUTINE_DECLARATION_INDEPENDENT"));
				String defaultTime = originalMembers.stream().filter(member -> member.name().equals("RD")).findFirst()
						.orElseThrow().attributes().get("MEMBER_CREATE_TIME");
				String implementationTime = originalMembers.stream().filter(member -> member.name().equals("RF"))
						.findFirst().orElseThrow().attributes().get("MEMBER_CREATE_TIME");
				assertTrue(defaultTime.compareTo(implementationTime) < 0);
				SqlFactory<com.sqlapp.data.schemas.Package> recreateModule = dialect.createSqlFactoryRegistry()
						.getSqlFactory(module, SqlType.CREATE);
				statement.execute("DROP MODULE " + schemaName + ".M");
				for (var operation : recreateModule.createSql(module))
					statement.execute(operation.getSqlText());
				Schema moduleActual = reader.getAllFull(connection).stream().filter(s -> schemaName.equals(s.getName()))
						.findFirst().orElseThrow();
				var rereadMembers = com.sqlapp.data.db.dialect.db2.Db2ModuleMembers
						.get(moduleActual.getPackages().get("M")).members();
				assertEquals(originalMembers.size(), rereadMembers.size());
				for (String prototype : List.of("FP", "PP")) {
					var member = rereadMembers.stream().filter(value -> value.name().equals(prototype)).findFirst()
							.orElseThrow();
					assertEquals("N", member.attributes().get("MODULEROUTINEIMPLEMENTED"));
					assertTrue(member.published());
				}
				var rereadRow = rereadMembers.stream()
						.filter(member -> member.kind() == com.sqlapp.data.db.dialect.db2.Db2ModuleMembers.Kind.TYPE
								&& member.name().equals("R"))
						.findFirst().orElseThrow();
				assertEquals(List.of("ID", "LABEL"), com.sqlapp.data.db.dialect.db2.Db2ModuleMembers
						.getRowType(rereadRow).getColumns().stream().map(TypeColumn::getName).toList());
				try (var rows = statement.executeQuery("VALUES " + schemaName + ".M.F(41)")) {
					assertTrue(rows.next());
					assertEquals(42, rows.getInt(1));
				}
				try (var rows = statement.executeQuery("VALUES " + schemaName + ".M.RF(4)")) {
					assertTrue(rows.next());
					assertEquals(4, rows.getInt(1));
				}
				try (var rows = statement.executeQuery("VALUES " + schemaName + ".M.RD")) {
					assertTrue(rows.next());
					assertEquals(4, rows.getInt(1));
				}
				try (var rows = statement.executeQuery("VALUES " + schemaName + ".M.V")) {
					assertTrue(rows.next());
					assertEquals(3, rows.getInt(1));
				}
				try (var call = connection.prepareCall("CALL " + schemaName + ".M.PQ(?)")) {
					call.registerOutParameter(1, java.sql.Types.INTEGER);
					call.execute();
					assertEquals(7, call.getInt(1));
				}
				try (var rows = statement.executeQuery("VALUES " + schemaName + ".M.FA(5)")) {
					assertTrue(rows.next());
					assertEquals(5, rows.getInt(1));
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
