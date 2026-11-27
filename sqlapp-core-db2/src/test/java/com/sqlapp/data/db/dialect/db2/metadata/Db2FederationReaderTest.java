/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.Db2Federation;
import com.sqlapp.data.db.dialect.db2.Db2Federation.*;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2FederationReaderTest {
	@Test
	void twentyWrappersAndServersAndDefaultsUseExactlyThreeSelects() {
		AtomicInteger queries = new AtomicInteger();
		var reader = new Db2FederationReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries.incrementAndGet();
				String query = node.toString();
				try {
					if (query.contains("FROM SYSCAT.WRAPPERS")) {
						assertTrue(query.contains("LEFT JOIN SYSCAT.WRAPOPTIONS"));
						for (int i = 0; i < 20; i++) {
							handler.handleResultSetNext(Db2MetadataParityTest
									.row(Map.of("WRAPNAME", "W" + i, "LIBRARY", "lib.so", "WRAPTYPE", "R",
											"WRAPVERSION", "1", "OPTION_NAME", "MODULE", "SETTING", " x's ")));
						}
						handler.handleResultSetNext(Db2MetadataParityTest.row(Map.of("WRAPNAME", "DRDA", "LIBRARY",
								"libdb2drda.so", "WRAPTYPE", "R", "WRAPVERSION", "1")));
					} else if (query.contains("FROM SYSCAT.SERVERS")) {
						for (int i = 0; i < 20; i++) {
							handler.handleResultSetNext(Db2MetadataParityTest
									.row(Map.of("SERVERNAME", "S" + i, "WRAPNAME", "W" + i, "SERVERTYPE", "ORACLE",
											"SERVERVERSION", "9i", "OPTION_NAME", "NODE", "SETTING", "service")));
						}
						handler.handleResultSetNext(Db2MetadataParityTest
								.row(Map.of("SERVERNAME", "S0", "WRAPNAME", "W0", "SERVERTYPE", "ORACLE",
										"SERVERVERSION", "9i", "OPTION_NAME", "DATE_COMPAT", "SETTING", "Y")));
						handler.handleResultSetNext(
								Db2MetadataParityTest.row(Map.of("SERVERNAME", "BARE", "WRAPNAME", "DRDA")));
					} else {
						assertTrue(query.contains("SERVERNAME IS NULL"));
						handler.handleResultSetNext(Db2MetadataParityTest
								.row(Map.of("SERVERTYPE", "ORACLE", "OPTION_NAME", "CPU_RATIO", "SETTING", "2.0")));
					}
				} catch (SQLException e) {
					throw new IllegalStateException(e);
				}
				return null;
			}
		};
		var snapshot = reader.read(null, new ParametersContext());
		assertEquals(3, queries.get());
		assertEquals(21, snapshot.wrappers().size());
		assertEquals(21, snapshot.servers().size());
		assertTrue(snapshot.wrappers().getLast().defaultLibrary());
		assertTrue(snapshot.wrappers().getLast().options().isEmpty());
		assertEquals(" x's ", snapshot.wrappers().getFirst().options().get("MODULE"));
		assertEquals("Y", snapshot.servers().getFirst().derivedOptions().get("DATE_COMPAT"));
		assertFalse(snapshot.servers().getFirst().options().containsKey("DATE_COMPAT"));
		assertEquals(1, snapshot.defaults().size());
		assertNull(snapshot.defaults().getFirst().wrapperName());
	}

	@Test
	void catalogLoadKeepsExistingSettingsAndExposesTypedSnapshot() {
		var snapshot = new Snapshot(1, List.of(new Wrapper("DRDA", "lib.so", true, Map.of(), null, null, Map.of())),
				List.of(), List.of());
		var reader = new Db2FederationReader(DialectHolder.defaultDialect) {
			@Override
			public Snapshot read(Connection connection) {
				return snapshot;
			}
		};
		Catalog catalog = new Catalog();
		catalog.getSettings().add(new com.sqlapp.data.schemas.Setting("EXISTING"));
		reader.loadFull(null, catalog);
		assertEquals(1, catalog.getSettings().size());
		assertEquals(snapshot, Db2Federation.get(catalog));
		assertNotNull(((Db2CatalogReader) DialectHolder.defaultDialect.getCatalogReader()).federation());
	}

	@Test
	void catalogFailuresPropagateAndDoNotEraseAnExistingSnapshot() {
		var reader = new Db2FederationReader(DialectHolder.defaultDialect) {
			@Override
			public Snapshot read(Connection connection) {
				throw new IllegalStateException("catalog access denied");
			}
		};
		Catalog catalog = new Catalog();
		Db2Federation.set(catalog, List.of(new Wrapper("DRDA", "lib.so", true, Map.of(), null, null, Map.of())),
				List.of(), List.of());
		assertThrows(IllegalStateException.class, () -> reader.loadFull(null, catalog));
		assertEquals(1, Db2Federation.get(catalog).wrappers().size());
	}
}
