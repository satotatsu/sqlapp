/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Embedded fixtures expose different product labels while retaining the HSQL
 * dialect. No real heterogeneous DB is used.
 */
class TransferDirectCompatibilityTest {
	@TempDir
	Path directory;

	@Test
	void warningPolicyAttemptsDirectCopyAndRetainsWarningsInSuccessfulOutcome() throws Exception {
		final var from = database();
		final var to = database();
		sql(from, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)", "INSERT INTO ITEMS VALUES(1),(2)");
		sql(to, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)");
		final var bindings = Map.of("from", from, "to", labeled(to));
		final var service = new TransferJobExecutionService();
		final var review = service.review(job(false), bindings, directory);
		assertFalse(review.prepared().warnings().isEmpty());
		assertEquals(0, count(to));
		final var result = service.execute(review, bindings);
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, result.phase());
		assertEquals(review.prepared().warnings(), result.prepared().warnings());
		assertTrue(result.verificationResult().isMatch());
		assertEquals(2, count(to));
	}

	@Test
	void strictPolicyRejectsDifferentProductLabelsBeforeCopyAndFailuresRetainWarnings() throws Exception {
		final var from = database();
		final var to = database();
		sql(from, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)", "INSERT INTO ITEMS VALUES(1),(2)");
		sql(to, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY CHECK(ID<2))");
		final var bindings = Map.of("from", from, "to", labeled(to));
		final var service = new TransferJobExecutionService();
		assertThrows(TransferSchemaPreparation.ValidationException.class,
				() -> service.review(job(true), bindings, directory));
		assertEquals(0, count(to));
		final var review = service.review(job(false), bindings, directory);
		final var failure = assertThrows(TransferJobExecutionService.ExecutionException.class,
				() -> service.execute(review, bindings));
		assertEquals(TransferJobExecutionService.Phase.COPY, failure.outcome().phase());
		assertEquals(review.prepared().warnings(), failure.outcome().prepared().warnings());
		assertEquals(1, count(to));
	}

	private Path job(boolean strict) throws Exception {
		return Files.writeString(directory.resolve("transfer.yaml"),
				"formatVersion: 1\nsource: from\ntarget: to\ntables: [ITEMS]\nschemaMode: REQUIRE_EXISTING\nchunkSize: 1\ncompatibilityPolicy: "
						+ (strict ? "ERROR" : "WARN") + "\n");
	}

	private static DataSource labeled(JDBCDataSource target) {
		return new JDBCDataSource() {
			@Override
			public Connection getConnection() throws SQLException {
				final var connection = target.getConnection();
				final var metadata = connection.getMetaData();
				final var labeledMetadata = (DatabaseMetaData) java.lang.reflect.Proxy.newProxyInstance(
						DatabaseMetaData.class.getClassLoader(), new Class<?>[] { DatabaseMetaData.class },
						(proxy, method, args) -> method.getName().equals("getDatabaseProductName")
								? "HSQL Compatibility Fixture"
								: invoke(metadata, method, args));
				return (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),
						new Class<?>[] { Connection.class },
						(proxy, method, args) -> method.getName().equals("getMetaData") ? labeledMetadata
								: invoke(connection, method, args));
			}
		};
	}

	private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
		try {
			return method.invoke(target, args);
		} catch (java.lang.reflect.InvocationTargetException failure) {
			throw failure.getCause();
		}
	}

	private static JDBCDataSource database() {
		final var ds = new JDBCDataSource();
		ds.setUrl("jdbc:hsqldb:mem:direct_compat_" + UUID.randomUUID());
		ds.setUser("SA");
		ds.setPassword("");
		return ds;
	}

	private static void sql(JDBCDataSource ds, String... statements) throws Exception {
		try (var c = ds.getConnection(); var s = c.createStatement()) {
			for (var sql : statements)
				s.execute(sql);
		}
	}

	private static int count(JDBCDataSource ds) throws Exception {
		try (var c = ds.getConnection();
				var s = c.createStatement();
				var r = s.executeQuery("SELECT COUNT(*) FROM ITEMS")) {
			r.next();
			return r.getInt(1);
		}
	}
}
