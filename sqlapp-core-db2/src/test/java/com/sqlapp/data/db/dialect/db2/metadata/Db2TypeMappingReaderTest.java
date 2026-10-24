/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.*;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2TypeMappingReaderTest {
	@Test void twentyMappingsUseOneQueryAndRetainRawConditionsInCreationOrder() {
		AtomicInteger count = new AtomicInteger();
		var reader = new Db2TypeMappingReader(DialectHolder.Db2_1110Dialect) {
			@Override protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context, ResultSetNextHandler handler) {
				count.incrementAndGet(); assertTrue(node.toString().contains("ORDER BY CREATE_TIME")); assertTrue(node.toString().contains("USER_DEFINED='Y'"));
				for (int i=19; i>=0; i--) try {
					handler.handleResultSetNext(Db2MetadataParityTest.row(Map.of("TYPE_MAPPING", "M"+i, "MAPPINGDIRECTION", i%2==0 ? "F" : "R", "TYPENAME", "DECIMAL", "S_OPR_P", "<=", "REMOTE_TYPENAME", "NUMBER", "REMOTE_LOWER_LEN", "-1", "CREATE_TIME", "t"+i)));
				} catch(SQLException e) { throw new IllegalStateException(e); }
				return null;
			}
		};
		var result = reader.read(null, new ParametersContext());
		assertEquals(1, count.get()); assertEquals(20, result.mappings().size()); assertTrue(result.orderKnown());
		assertEquals("M19", result.mappings().getFirst().name());
		assertEquals(Db2TypeMappings.Direction.REVERSE, result.mappings().getFirst().direction());
		assertEquals("<=", result.mappings().getFirst().attributes().get("S_OPR_P"));
		assertEquals("-1", result.mappings().getFirst().attributes().get("REMOTE_LOWER_LEN"));
		assertNotNull(((Db2CatalogReader)DialectHolder.Db2_1215Dialect.getCatalogReader()).typeMappings());
	}
	@Test void invalidDirectionAndOlderVersionFailAndReadErrorsDoNotReplaceStoredMetadata() {
		assertThrows(IllegalArgumentException.class, () -> new Db2TypeMappingReader(DialectHolder.Db2_1050Dialect).read(null, new ParametersContext()));
		var reader = new Db2TypeMappingReader(DialectHolder.Db2_1110Dialect) {
			@Override protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				try { h.handleResultSetNext(Db2MetadataParityTest.row(Map.of("MAPPINGDIRECTION", "X"))); }
				catch(SQLException e) { throw new IllegalStateException(e); } return null;
			}
		};
		assertThrows(IllegalStateException.class, () -> reader.read(null, new ParametersContext()));
		var catalog = new com.sqlapp.data.schemas.Catalog(); catalog.getSpecifics().put("other", "retained");
		var failing = new Db2TypeMappingReader(DialectHolder.Db2_1110Dialect) {
			@Override public Db2TypeMappings.Snapshot read(Connection connection) { throw new IllegalStateException("catalog denied"); }
		};
		assertThrows(IllegalStateException.class, () -> failing.loadFull(null, catalog)); assertEquals("retained", catalog.getSpecifics().get("other"));
	}
}
