/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.Db2UserMappings;
import com.sqlapp.data.db.dialect.db2.Db2UserMappings.*;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2UserMappingReaderTest {
	@Test
	void twentyMappingsUseOneQueryWithoutReadingEncryptedPasswordValues() {
		AtomicInteger queries = new AtomicInteger();
		var reader = new Db2UserMappingReader(DialectHolder.Db2_1110Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries.incrementAndGet();
				assertTrue(node.toString().contains("THEN CAST(NULL AS VARCHAR(2048))"));
				for (int i = 0; i < 20; i++) {
					try {
						handler.handleResultSetNext(Db2MetadataParityTest.row(Map.of("AUTHID", "U" + i, "AUTHIDTYPE",
								"U", "SERVERNAME", "S", "OPTION_NAME", "REMOTE_AUTHID", "SETTING", " remote's id ")));
						final int ordinal = i;
						handler.handleResultSetNext(new ExResultSet(null) {
							@Override
							public String getString(String name) {
								if ("SETTING".equals(name))
									throw new AssertionError("Encrypted password value must not be read");
								return Map.of("AUTHID", "U" + ordinal, "AUTHIDTYPE", "U", "SERVERNAME", "S",
										"OPTION_NAME", "REMOTE_PASSWORD").get(name);
							}
						});
					} catch (SQLException e) {
						throw new IllegalStateException(e);
					}
				}
				return null;
			}
		};
		var snapshot = reader.read(null, new ParametersContext());
		assertEquals(1, queries.get());
		assertEquals(20, snapshot.mappings().size());
		assertEquals(" remote's id ", snapshot.mappings().getFirst().options().get("REMOTE_AUTHID"));
		assertEquals(List.of("REMOTE_PASSWORD"), snapshot.mappings().getFirst().unavailableOptions());
		assertFalse(snapshot.mappings().getFirst().options().containsKey("REMOTE_PASSWORD"));
	}

	@Test
	void failuresPropagateWithoutReplacingStoredMappingsAndOldReadersReject() {
		var mapping = new Mapping(Principal.USER, "U", "S", Map.of(), List.of("REMOTE_PASSWORD"), null);
		Catalog catalog = new Catalog();
		Db2UserMappings.set(catalog, List.of(mapping));
		var reader = new Db2UserMappingReader(DialectHolder.Db2_1110Dialect) {
			@Override
			public Snapshot read(Connection connection) {
				throw new IllegalStateException("catalog access denied");
			}
		};
		assertThrows(IllegalStateException.class, () -> reader.loadFull(null, catalog));
		assertEquals(List.of(mapping), Db2UserMappings.get(catalog).mappings());
		assertThrows(IllegalArgumentException.class,
				() -> new Db2UserMappingReader(DialectHolder.defaultDialect).read(null, new ParametersContext()));
		assertNotNull(((Db2CatalogReader) DialectHolder.Db2_1215Dialect.getCatalogReader()).userMappings());
	}

	@Test
	void publicMappingsLoadWithoutPasswordsAndInvalidCatalogRowsFailClearly() {
		var valid = Map.of("AUTHID", "PUBLIC", "AUTHIDTYPE", "U", "SERVERNAME", "S", "OPTION_NAME", "REMOTE_PASSWORD");
		var reader = rows(valid, false);
		var snapshot = reader.read(null, new ParametersContext());
		assertEquals(Principal.PUBLIC, snapshot.mappings().getFirst().principal());
		Catalog catalog = new Catalog();
		catalog.getSettings().add(new com.sqlapp.data.schemas.Setting("EXISTING"));
		var loaded = new Db2UserMappingReader(DialectHolder.Db2_1110Dialect) {
			@Override
			public Snapshot read(Connection connection) {
				return snapshot;
			}
		};
		loaded.loadFull(null, catalog);
		assertEquals(snapshot, Db2UserMappings.get(catalog));
		assertEquals(1, catalog.getSettings().size());
		assertThrows(IllegalStateException.class, () -> rows(valid, true).read(null, new ParametersContext()));
		var invalid = new java.util.HashMap<>(valid);
		invalid.put("AUTHIDTYPE", "G");
		assertThrows(IllegalStateException.class, () -> rows(invalid, false).read(null, new ParametersContext()));
		invalid.put("AUTHIDTYPE", "U");
		invalid.put("OPTION_NAME", "REMOTE_AUTHID");
		assertThrows(IllegalStateException.class, () -> rows(invalid, false).read(null, new ParametersContext()));
	}

	private Db2UserMappingReader rows(Map<String, String> row, boolean duplicate) {
		return new Db2UserMappingReader(DialectHolder.Db2_1110Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				try {
					handler.handleResultSetNext(Db2MetadataParityTest.row(row));
					if (duplicate)
						handler.handleResultSetNext(Db2MetadataParityTest.row(row));
				} catch (SQLException e) {
					throw new IllegalStateException(e);
				}
				return null;
			}
		};
	}
}
