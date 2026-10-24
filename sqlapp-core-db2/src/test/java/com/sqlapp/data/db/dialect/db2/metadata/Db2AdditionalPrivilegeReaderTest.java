/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.*;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2AdditionalPrivilegeReaderTest {
	@Test
	void sixKindsAcrossTwentyObjectsUseOneQuery() {
		AtomicInteger queries = new AtomicInteger();
		var reader = new Db2ExtendedObjectPrivilegeReader(DialectHolder.Db2_1110Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries.incrementAndGet();
				for (String view : new String[] { "TBSPACEAUTH", "PACKAGEAUTH", "MODULEAUTH", "VARIABLEAUTH",
						"WORKLOADAUTH", "XSROBJECTAUTH", "XSROBJECTS" })
					assertTrue(node.toString().contains("SYSCAT." + view), view);
				assertTrue(node.toString().contains("VARMODULEID IS NULL"));
				for (int i = 0; i < 20; i++)
					for (String kind : new String[] { "TABLESPACE", "PACKAGE", "MODULE", "VARIABLE", "WORKLOAD",
							"XSROBJECT" }) {
						var row = new java.util.HashMap<>(Map.of("OBJECT_TYPE", kind, "object_name", "SAME" + i,
								"GRANTOR", "OWNER", "GRANTEE", "U", "GRANTEETYPE", "U", "AUTH_STATE", "Y"));
						if (!java.util.Set.of("TABLESPACE", "WORKLOAD").contains(kind))
							row.put("schema_name", "APP");
						row.put("PRIVILEGE", switch (kind) {
						case "TABLESPACE" -> "USE";
						case "PACKAGE", "MODULE" -> "EXECUTE";
						case "VARIABLE" -> "READ";
						default -> "USAGE";
						});
						try {
							handler.handleResultSetNext(Db2MetadataParityTest.row(row));
						} catch (SQLException e) {
							throw new IllegalStateException(e);
						}
					}
				return null;
			}
		};
		assertEquals(120, reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo()).size());
		assertEquals(1, queries.get());
	}

	@Test
	void oldDialectsNeverReferenceNewCatalogs() {
		for (Dialect dialect : new Dialect[] { DialectHolder.defaultDialect, DialectHolder.Db2_950Dialect,
				DialectHolder.Db2_970Dialect, DialectHolder.Db2_1010Dialect, DialectHolder.Db2_1050Dialect,
				DialectHolder.Db2_1110Dialect, DialectHolder.Db2_1215Dialect }) {
			var reader = new Db2ExtendedObjectPrivilegeReader(dialect) {
				@Override
				protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
						ResultSetNextHandler handler) {
					assertEquals(dialect instanceof Db2_1110, node.toString().contains("SYSCAT.MODULEAUTH"));
					return null;
				}
			};
			reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		}
	}
}
