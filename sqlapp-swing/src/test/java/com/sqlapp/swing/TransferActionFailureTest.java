/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.command.migration.transfer.TransferJobPreparationService;

class TransferActionFailureTest {
	@TempDir
	Path directory;

	@ParameterizedTest
	@CsvSource({ "28P01,authentication,username/password", "3D000,database,database name",
			"08001,connection,server/port", "42501,permissions,metadata/table access" })
	void sqlStateProducesSpecificGuidanceWithoutDriverMessages(String state, String reason, String next)
			throws Exception {
		final var file = Files.writeString(directory.resolve("job.yaml"),
				"formatVersion: 1\nsource: from\ntarget: to\ntables: [ITEMS]\nschemaMode: REQUIRE_EXISTING\n");
		final DataSource source = dataSource(false, state);
		final DataSource target = dataSource(true, state);
		final var failure = assertThrows(TransferJobPreparationService.PreparationException.class,
				() -> new TransferJobPreparationService().prepare(file, Map.of("from", source, "to", target),
						directory));
		final var diagnostic = TransferActionFailure.preparation(failure);
		final var summary = diagnostic.summary();
		assertTrue(summary.contains("opening target connection"));
		assertTrue(summary.contains(reason));
		assertTrue(summary.contains(next));
		assertTrue(summary.contains(state));
		assertFalse(summary.contains("driver-secret"));
		assertFalse(summary.contains("jdbc:private"));
		assertNull(diagnostic.getCause());
		assertEquals(0, diagnostic.getSuppressed().length);
	}

	private static DataSource dataSource(boolean fail, String state) {
		return (DataSource) java.lang.reflect.Proxy.newProxyInstance(DataSource.class.getClassLoader(),
				new Class<?>[] { DataSource.class }, (proxy, method, args) -> {
					if (!method.getName().equals("getConnection"))
						throw new UnsupportedOperationException();
					if (fail)
						throw new SQLException("driver-secret jdbc:private", state, 7);
					return java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),
							new Class<?>[] { Connection.class }, (connection, operation, parameters) -> {
								if (operation.getName().equals("close"))
									return null;
								throw new UnsupportedOperationException();
							});
				});
	}
}
