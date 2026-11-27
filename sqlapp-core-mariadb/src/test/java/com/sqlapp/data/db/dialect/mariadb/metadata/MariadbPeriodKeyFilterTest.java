/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class MariadbPeriodKeyFilterTest {
	private static class Reader extends MariadbUniqueConstraint11_40Reader {
		int queries;
		boolean empty;

		Reader() {
			super(DialectResolver.getInstance().getDialect("MariaDB", 11, 4, 0));
		}

		@Override
		protected boolean filterObject(UniqueConstraint object) {
			// TableObjectReader deliberately accepts all objects by default. Model an
			// extension that opts into predicate filtering without changing that default.
			return getReadDbObjectPredicate() == null || getReadDbObjectPredicate().test(object, this);
		}

		@Override
		public ProductVersionInfo getProductVersionInfo(Connection c) {
			return new ProductVersionInfo().setName("MariaDB").setMajorVersion(11).setMinorVersion(4).setRevision(0);
		}

		@Override
		protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
			queries++;
			boolean periods = n.eval(p).getSql().contains("KEY_PERIOD_USAGE");
			try {
				if (!empty)
					for (String schema : List.of("app", "audit")) {
						for (String column : periods ? List.of("period") : List.of("id", "valid_from", "valid_until")) {
							h.handleResultSetNext(new ExResultSet(null) {
								@Override
								public String getString(String name) {
									return switch (name.toUpperCase(Locale.ROOT)) {
									case "TABLE_CATALOG", "CONSTRAINT_CATALOG" -> "def";
									case "TABLE_SCHEMA", "CONSTRAINT_SCHEMA" -> schema;
									case "TABLE_NAME" -> "history";
									case "CONSTRAINT_NAME" -> "unique_time";
									case "CONSTRAINT_TYPE" -> "UNIQUE";
									case "COLUMN_NAME" -> column;
									case "PERIOD_NAME" -> "valid_time";
									case "INDEX_TYPE" -> "BTREE";
									default -> null;
									};
								}

								@Override
								public long getLong(String name) {
									return 0;
								}

								@Override
								public boolean wasNull() {
									return true;
								}
							});
						}
					}
			} catch (java.sql.SQLException e) {
				throw new IllegalStateException(e);
			}
			return null;
		}
	}

	@Test
	void periodKeysAreEnrichedBeforePredicateAndRemainBatched() {
		var reader = new Reader();
		reader.setReadDbObjectPredicate((object, source) -> {
			var key = (UniqueConstraint) object;
			assertEquals("valid_time", key.getSpecifics().get("mariadb.withoutOverlapsPeriod"));
			return key.getSchemaName().equals("app");
		});
		var keys = reader.getAll(null, new ParametersContext());
		assertEquals(2, reader.queries);
		assertEquals(1, keys.size());
		assertEquals("app", keys.get(0).getSchemaName());
		assertEquals(3, keys.get(0).getColumns().size());
	}

	@Test
	void emptyDiscoverySkipsPeriodQueryAndVersionBoundaryIsPreserved() {
		var reader = new Reader();
		reader.empty = true;
		assertTrue(reader.getAll(null, new ParametersContext()).isEmpty());
		assertEquals(1, reader.queries);
		var old = DialectResolver.getInstance().getDialect("MariaDB", 11, 3, 0);
		assertEquals("MySqlUniqueConstraintReader", old.getCatalogReader().getSchemaReader().getTableReader()
				.getUniqueConstraintReader().getClass().getSimpleName());
		var modern = DialectResolver.getInstance().getDialect("MariaDB", 11, 4, 0);
		assertInstanceOf(MariadbUniqueConstraint11_40Reader.class,
				modern.getCatalogReader().getSchemaReader().getTableReader().getUniqueConstraintReader());
	}
}
