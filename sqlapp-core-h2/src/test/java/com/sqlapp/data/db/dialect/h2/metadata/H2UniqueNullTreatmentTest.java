/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import static com.sqlapp.data.db.dialect.h2.metadata.H2DialectRoundTripTest.*;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.h2.H2_220;
import com.sqlapp.data.db.sql.SqlType;

class H2UniqueNullTreatmentTest {
	@ParameterizedTest
	@CsvSource({ "DISTINCT,YES,false,false", "NOT DISTINCT,NO,true,true", "ALL DISTINCT,ALL,true,false" })
	void recreatesCompositeNullTreatment(String clause, String policy, boolean mixedRejected, boolean allRejected)
			throws Exception {
		try (var source = DriverManager.getConnection("jdbc:h2:mem:");
				var target = DriverManager.getConnection("jdbc:h2:mem:");
				var command = source.createStatement()) {
			command.execute("CREATE TABLE T(A INT,B INT,CONSTRAINT U UNIQUE NULLS " + clause + "(A,B))");
			var table = read(source, "PUBLIC").getTables().get("T");
			assertEquals(policy, table.getConstraints().get("U").getSpecifics().get("h2.nullsDistinct"));
			String xml = table.asXml();
			create(target, table.clone());
			assertEquals(xml, table.asXml());
			assertEquals(policy, read(target, "PUBLIC").getTables().get("T").getConstraints().get("U").getSpecifics()
					.get("h2.nullsDistinct"));
			for (var connection : new java.sql.Connection[] { source, target }) {
				try (var statement = connection.createStatement()) {
					statement.execute("INSERT INTO T VALUES(NULL,1),(NULL,NULL)");
					if (mixedRejected) {
						assertThrows(SQLException.class, () -> statement.execute("INSERT INTO T VALUES(NULL,1)"));
					} else {
						statement.execute("INSERT INTO T VALUES(NULL,1)");
					}
					if (allRejected) {
						assertThrows(SQLException.class, () -> statement.execute("INSERT INTO T VALUES(NULL,NULL)"));
					} else {
						statement.execute("INSERT INTO T VALUES(NULL,NULL)");
					}
				}
			}
		}
	}

	@Test
	void catalogColumnsAreIsolatedByVersion() throws Exception {
		var reader = new H2UniqueConstraintReader(DialectResolver.getInstance().getDialect("H2", 2, 3, 232));
		for (int minor : new int[] { 0, 1, 2, 3 }) {
			var version = new com.sqlapp.data.schemas.ProductVersionInfo().setMajorVersion(2).setMinorVersion(minor);
			try (var sql = reader.getSqlSqlNode(version).eval(new com.sqlapp.data.parameter.ParametersContext())) {
				assertEquals(minor >= 2, sql.getSql().contains("tc.NULLS_DISTINCT"));
			}
		}
	}

	@ParameterizedTest
	@CsvSource({ "REGULAR,YES", "Oracle,ALL", "MSSQLServer,NO" })
	void recreatesImplicitCompatibilityModePolicyInRegularMode(String mode, String policy) throws Exception {
		try (var source = DriverManager.getConnection("jdbc:h2:mem:;MODE=" + mode);
				var target = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = source.createStatement()) {
			statement.execute("CREATE TABLE T(A INT,B INT,CONSTRAINT U UNIQUE(A,B))");
			var table = read(source, "PUBLIC").getTables().get("T");
			assertEquals(policy, table.getConstraints().get("U").getSpecifics().get("h2.nullsDistinct"));
			create(target, table);
			assertEquals(policy, read(target, "PUBLIC").getTables().get("T").getConstraints().get("U").getSpecifics()
					.get("h2.nullsDistinct"));
		}
	}

	@Test
	void standaloneConstraintAndOldVersionValidation() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE T(A INT,CONSTRAINT U UNIQUE NULLS NOT DISTINCT(A))");
			var constraint = read(connection, "PUBLIC").getTables().get("T").getConstraints().get("U");
			var resolver = DialectResolver.getInstance();
			for (int minor : new int[] { 0, 1 }) {
				var dialect = resolver.getDialect("H2", 2, minor, 0);
				assertFalse(dialect instanceof H2_220);
				assertThrows(IllegalArgumentException.class,
						() -> dialect.createSqlFactoryRegistry().createSql(constraint, SqlType.CREATE));
			}
			assertInstanceOf(H2_220.class, resolver.getDialect("H2", 2, 2, 220));
			statement.execute("ALTER TABLE T DROP CONSTRAINT U");
			create(connection, constraint);
			statement.execute("INSERT INTO T VALUES(NULL)");
			assertThrows(SQLException.class, () -> statement.execute("INSERT INTO T VALUES(NULL)"));
			constraint.getSpecifics().put("h2.nullsDistinct", "invalid");
			assertThrows(IllegalArgumentException.class, () -> create(connection, constraint));
		}
	}
}
