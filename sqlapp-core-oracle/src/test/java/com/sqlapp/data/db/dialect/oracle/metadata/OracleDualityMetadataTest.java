/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.dialect.oracle.sql.OracleJsonDualityViewUtils;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.View;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class OracleDualityMetadataTest {
	private ExResultSet row(Map<String, String> values) {
		return new ExResultSet(null) {
			@Override public String getString(String column) { return values.get(column); }
			@Override public boolean getBoolean(String column) { return false; }
		};
	}

	@Test
	void collectionIsReadOnceAndQuotedIdentitiesRemainDistinct() throws Exception {
		AtomicInteger count = new AtomicInteger();
		Oracle26aiViewReader reader = new Oracle26aiViewReader(DialectHolder.oracle26aiDialect) {
			@Override public ColumnReader getColumnReader() { return null; }
			@Override protected JdbcQueryHandler execute(Connection connection, SqlNode node,
					ParametersContext context, ResultSetNextHandler handler) {
				count.incrementAndGet();
				try {
					handler.handleResultSetNext(row(Map.of("VIEW_OWNER", "App", "VIEW_NAME", "Dv",
							"LOGICAL_REPLICATION", "ENABLED")));
				} catch (SQLException e) { throw new RuntimeException(e); }
				return null;
			}
		};
		View upper = new View("DV");
		upper.setSchemaName("APP");
		View mixed = new View("Dv");
		mixed.setSchemaName("App");
		View otherOwner = new View("Dv");
		otherOwner.setSchemaName("Other");
		View otherCase = new View("DV");
		otherCase.setSchemaName("App");
		reader.setMetadataDetail(null, new ParametersContext(), List.<Table>of(upper, otherCase, mixed, otherOwner));
		assertEquals(1, count.get());
		assertFalse(OracleJsonDualityViewUtils.isJsonRelationalDualityView(upper));
		assertFalse(OracleJsonDualityViewUtils.isJsonRelationalDualityView(otherOwner));
		assertFalse(OracleJsonDualityViewUtils.isJsonRelationalDualityView(otherCase));
		assertTrue(OracleJsonDualityViewUtils.isJsonRelationalDualityView(mixed));
		assertEquals("true", mixed.getSpecifics().get(OracleJsonDualityViewUtils.LOGICAL_REPLICATION));
	}

	@Test
	void replicationStateAndVersionRoutingAreExplicit() throws Exception {
		var reader = new Oracle26aiViewReader(DialectHolder.oracle26aiDialect);
		for (String state : List.of("ENABLED", "DISABLED")) {
			View view = new View("DV");
			reader.setDualityMetadata(view, row(Map.of("LOGICAL_REPLICATION", state)));
			assertEquals(Boolean.toString(state.equals("ENABLED")),
					view.getSpecifics().get(OracleJsonDualityViewUtils.LOGICAL_REPLICATION));
		}
		assertThrows(IllegalStateException.class, () -> reader.setDualityMetadata(new View("DV"),
				row(Map.of("LOGICAL_REPLICATION", "UNKNOWN"))));
		assertInstanceOf(Oracle26aiViewReader.class, DialectHolder.oracle26aiDialect.getCatalogReader().getSchemaReader().getViewReader());
		assertEquals(Oracle23aiViewReader.class, DialectHolder.oracle23aiDialect.getCatalogReader().getSchemaReader().getViewReader().getClass());
		assertInstanceOf(Oracle23aiColumnReader.class, DialectHolder.oracle26aiDialect.getCatalogReader()
				.getSchemaReader().getViewReader().getColumnReader());
		assertInstanceOf(Oracle23aiColumnReader.class, DialectHolder.oracle23aiDialect.getCatalogReader()
				.getSchemaReader().getViewReader().getColumnReader());
		assertEquals(OracleColumnReader.class, DialectHolder.oracle21cDialect.getCatalogReader()
				.getSchemaReader().getViewReader().getColumnReader().getClass());
		try (var legacy = Oracle23aiViewReader.class.getResourceAsStream("jsonRelationalDualityViews.sql");
				var modern = Oracle26aiViewReader.class.getResourceAsStream("jsonRelationalDualityViews26ai.sql")) {
			assertFalse(new String(legacy.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains("LOGICAL_REPLICATION"));
			assertTrue(new String(modern.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains("LOGICAL_REPLICATION"));
		}
	}
}
