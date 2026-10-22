/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.hsql.HsqlTableMigration;
import com.sqlapp.data.db.dialect.hsql.HsqlTextTableSources;
import com.sqlapp.data.db.dialect.hsql.HsqlTableMigration.Access;
import com.sqlapp.data.db.dialect.hsql.HsqlTableMigration.Phase;
import com.sqlapp.data.schemas.Catalog;

class HsqlTextMigrationTest {
	@TempDir
	Path directory;

	@Test
	void explicitEmptyTargetsMigrateAndOriginalFilesSurviveRecovery() throws Exception {
		for (boolean batch : java.util.List.of(false, true))
			for (boolean fail : java.util.List.of(false, true)) {
				Path folder = Files.createDirectory(directory.resolve("case-" + batch + "-" + fail));
				Files.writeString(folder.resolve("input.csv"), "2|alpha\n3|beta\n");
				Files.writeString(folder.resolve("output.csv"), "");
				try (var c = DriverManager.getConnection("jdbc:hsqldb:file:" + folder.resolve("db"), "SA", "");
						var s = c.createStatement()) {
					try {
						s.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
						s.execute("CREATE TEXT TABLE APP.T(N INTEGER,V VARCHAR(40))");
						s.execute("SET TABLE APP.T SOURCE 'input.csv;fs=|;encoding=UTF-8'");
						var source = read(c);
						var target = source.clone();
						var table = target.getSchemas().get("APP").getTables().get("T");
						table.getColumns().get("N").setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT);
						if (fail)
							table.getConstraints().add(new com.sqlapp.data.schemas.CheckConstraint("BAD", "N<0"));
						var dialect = DialectResolver.getInstance().getDialect(c);
						assertThrows(IllegalArgumentException.class,
								() -> new HsqlTableMigration(dialect).planAll(source, target));
						var migration = new HsqlTableMigration(dialect, Map.of(HsqlTextTableSources.id(table),
								new HsqlTextTableSources.Source("output.csv;fs=|;encoding=UTF-8", false)));
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
						try (var rs = s.executeQuery("SELECT SUM(N) FROM APP.T")) {
							assertTrue(rs.next());
							assertEquals(5, rs.getInt(1));
						}
						assertTrue(HsqlTextTableSources.get(read(c).getSchemas().get("APP").getTables().get("T"))
								.definition().contains(fail ? "input.csv" : "output.csv"));
						assertFalse(Boolean.TRUE
								.equals(read(c).getSchemas().get("APP").getTables().get("T").getReadonly()));
					} finally {
						s.execute("SHUTDOWN");
					}
				}
				assertEquals("2|alpha\n3|beta\n", Files.readString(folder.resolve("input.csv")));
			}
	}

	@Test
	void occupiedTargetIsNotOverwrittenAndChangedOriginalFileBlocksRecovery() throws Exception {
		for (boolean drift : java.util.List.of(false, true)) {
			Path folder = Files.createDirectory(directory.resolve("occupied-" + drift));
			Files.writeString(folder.resolve("input.csv"), "2\n3\n");
			Files.writeString(folder.resolve("occupied.csv"), "9\n");
			try (var c = DriverManager.getConnection("jdbc:hsqldb:file:" + folder.resolve("db"), "SA", "");
					var s = c.createStatement()) {
				try {
					s.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
					s.execute("CREATE TEXT TABLE APP.T(N INTEGER)");
					s.execute("SET TABLE APP.T SOURCE 'input.csv;encoding=UTF-8'");
					var source = read(c);
					var target = source.clone();
					var table = target.getSchemas().get("APP").getTables().get("T");
					table.getColumns().get("N").setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT);
					var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(c),
							Map.of(HsqlTextTableSources.id(table),
									new HsqlTextTableSources.Source("occupied.csv;encoding=UTF-8", false)));
					var plan = migration.planAll(source, target);
					var failure = assertThrows(HsqlTableMigration.BatchFailure.class,
							() -> migration.execute(c, plan, Access.EXCLUSIVE));
					assertEquals(Phase.RESTORE, failure.getPhase());
					if (drift) {
						Files.writeString(folder.resolve("input.csv"), "8\n");
						assertEquals(Phase.RECOVER, assertThrows(HsqlTableMigration.BatchFailure.class,
								() -> migration.recover(c, failure, Access.EXCLUSIVE)).getPhase());
					} else
						assertEquals(Phase.RECOVERED, migration.recover(c, failure, Access.EXCLUSIVE).phase());
				} finally {
					s.execute("SHUTDOWN");
				}
			}
			assertEquals("9\n", Files.readString(folder.resolve("occupied.csv")));
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

	@Test
	void textAndMemoryStorageCanBeConvertedAndRecovered() throws Exception {
		for (boolean fromText : java.util.List.of(false, true))
			for (boolean fail : java.util.List.of(false, true)) {
				Path folder = Files.createDirectory(directory.resolve("storage-" + fromText + "-" + fail));
				Files.writeString(folder.resolve("input.csv"), "2\n3\n");
				Files.writeString(folder.resolve("output.csv"), "");
				try (var c = DriverManager.getConnection("jdbc:hsqldb:file:" + folder.resolve("db"), "SA", "");
						var s = c.createStatement()) {
					try {
						s.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
						s.execute("CREATE " + (fromText ? "TEXT" : "MEMORY") + " TABLE APP.T(N INTEGER)");
						if (fromText)
							s.execute("SET TABLE APP.T SOURCE 'input.csv;encoding=UTF-8'");
						else
							s.execute("INSERT INTO APP.T VALUES(2),(3)");
						var source = read(c);
						var target = source.clone();
						var table = target.getSchemas().get("APP").getTables().get("T");
						if (fromText) {
							table.getSpecifics().remove(HsqlTextTableSources.SPECIFICS_KEY);
							table.getSpecifics().remove(HsqlTextTableSources.TABLE_TYPE_KEY);
							table.setTableType(com.sqlapp.data.schemas.Table.TableType.Memory);
						} else
							HsqlTextTableSources.record(table, "placeholder.csv", false);
						if (fail)
							table.getConstraints().add(new com.sqlapp.data.schemas.CheckConstraint("BAD", "N<0"));
						var migration = fromText ? new HsqlTableMigration(DialectResolver.getInstance().getDialect(c))
								: new HsqlTableMigration(DialectResolver.getInstance().getDialect(c),
										Map.of(HsqlTextTableSources.id(table),
												new HsqlTextTableSources.Source("output.csv;encoding=UTF-8", false)));
						var plan = migration.planAll(source, target);
						if (fail) {
							var failure = assertThrows(HsqlTableMigration.BatchFailure.class,
									() -> migration.execute(c, plan, Access.EXCLUSIVE));
							assertEquals(Phase.RESTORE, failure.getPhase());
							assertEquals(Phase.RECOVERED, migration.recover(c, failure, Access.EXCLUSIVE).phase());
						} else
							assertEquals(Phase.COMPLETE, migration.execute(c, plan, Access.EXCLUSIVE).phase());
						assertEquals(fail ? fromText : !fromText,
								HsqlTextTableSources.isText(read(c).getSchemas().get("APP").getTables().get("T")));
						try (var rs = s.executeQuery("SELECT SUM(N) FROM APP.T")) {
							assertTrue(rs.next());
							assertEquals(5, rs.getInt(1));
						}
					} finally {
						s.execute("SHUTDOWN");
					}
				}
				assertEquals("2\n3\n", Files.readString(folder.resolve("input.csv")));
			}
	}

	@Test
	void tinyOriginalFilesRespectThePlatformCacheBoundaryBeforeBackup() throws Exception {
		for (String content : java.util.List.of("", "2\n")) {
			Path folder = Files.createDirectory(directory.resolve(content.isEmpty() ? "empty-source" : "short-source"));
			Files.writeString(folder.resolve("tiny.csv"), content);
			Files.writeString(folder.resolve("empty.csv"), "");
			try (var c = DriverManager.getConnection("jdbc:hsqldb:file:" + folder.resolve("db"), "SA", "");
					var s = c.createStatement()) {
				try {
					s.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
					s.execute("CREATE TEXT TABLE APP.T(N INTEGER)");
					s.execute("SET TABLE APP.T SOURCE 'tiny.csv;encoding=UTF-8'");
					var source = read(c);
					var target = source.clone();
					var table = target.getSchemas().get("APP").getTables().get("T");
					table.getColumns().get("N").setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT);
					var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(c),
							Map.of(HsqlTextTableSources.id(table),
									new HsqlTextTableSources.Source("empty.csv;encoding=UTF-8", false)));
					var plan = migration.planAll(source, target);
					if (Files.size(folder.resolve("tiny.csv")) <= System.lineSeparator()
							.getBytes(java.nio.charset.StandardCharsets.UTF_8).length) {
						var failure = assertThrows(HsqlTableMigration.BatchFailure.class,
								() -> migration.execute(c, plan, Access.EXCLUSIVE));
						assertEquals(Phase.PREFLIGHT, failure.getPhase());
						assertTrue(failure.getBackups().isEmpty());
						assertTrue(failure.getCause().getMessage().contains("tiny"));
					} else
						assertEquals(Phase.COMPLETE, migration.execute(c, plan, Access.EXCLUSIVE).phase());
					assertEquals(content, Files.readString(folder.resolve("tiny.csv")));
				} finally {
					s.execute("SHUTDOWN");
				}
			}
		}
	}
}
