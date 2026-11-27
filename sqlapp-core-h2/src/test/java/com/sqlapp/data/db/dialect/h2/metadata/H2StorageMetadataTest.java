/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import static com.sqlapp.data.db.dialect.h2.metadata.H2DialectRoundTripTest.*;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

class H2StorageMetadataTest {
	@ParameterizedTest
	@ValueSource(strings = { "CACHED", "MEMORY" })
	void preservesExplicitStorageInIsolatedFileDatabases(String storage, @TempDir Path directory) throws Exception {
		try (var source = DriverManager.getConnection("jdbc:h2:" + directory.resolve("source").toAbsolutePath());
				var target = DriverManager.getConnection("jdbc:h2:" + directory.resolve("target").toAbsolutePath());
				var statement = source.createStatement()) {
			statement.execute("CREATE " + storage + " TABLE T(ID INT)");
			var model = read(source, "PUBLIC").getTables().get("T");
			create(target, model);
			assertEquals(storage.equals("CACHED") ? Table.TableType.Cache : Table.TableType.Memory,
					read(target, "PUBLIC").getTables().get("T").getTableType());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "LOCAL", "GLOBAL" })
	void doesNotSilentlyTurnReadTemporaryTablesIntoPermanentTables(String scope) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE " + scope + " TEMPORARY TABLE T(ID INT)");
			statement.execute("INSERT INTO T VALUES(7)");
			var model = read(connection, "PUBLIC").getTables().get("T");
			assertEquals(Table.TableType.Temporary, model.getTableType());
			assertEquals(scope + " TEMPORARY", model.getSpecifics().get("h2.tableKind"));
			var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(model, SqlType.CREATE));
			try (var rows = statement.executeQuery("SELECT ID FROM T")) {
				assertTrue(rows.next());
				assertEquals(7, rows.getInt(1));
			}
		}
	}
}
