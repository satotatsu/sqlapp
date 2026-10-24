/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class FirebirdDomainMetadataTest {
	@Test
	void characterLengthIsNotTheUtf8ByteLength() throws Exception {
		var reader = new FirebirdDomainReader(DialectHolder.defaultDialect25);
		var domain = reader.createDomain(row(Map.of("RDB$FIELD_NAME", " text_domain ", "RDB$FIELD_TYPE", 37,
				"RDB$FIELD_LENGTH", 48, "RDB$CHARACTER_LENGTH", 12, "CHARACTER_SET_NAME", "UTF8", "COLLATION_NAME",
				"UNICODE_CI", "RDB$DESCRIPTION", "日本語")));
		assertEquals("text_domain", domain.getName());
		assertEquals(12L, domain.getLength());
		assertEquals("UTF8", domain.getCharacterSet());
		assertEquals("UNICODE_CI", domain.getCollation());
		assertEquals("日本語", domain.getRemarks());
	}

	@Test
	void fiftyArrayDomainsRequireOnlyTwoBulkQueries() {
		int[] queries = { 0 };
		var reader = reader(queries, false);
		var domains = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(50, domains.size());
		assertEquals(2, queries[0]);
		for (var domain : domains) {
			assertEquals(2, domain.getArrayDimension());
			assertEquals("-2:2,3:4", domain.getSpecifics().get("firebird.array.bounds"));
		}
	}

	@Test
	void incompleteArrayMetadataFailsInsteadOfLosingDimensions() {
		assertThrows(IllegalStateException.class,
				() -> reader(new int[] { 0 }, true).doGetAll(null, new ParametersContext(), null));
	}

	private FirebirdDomainReader reader(int[] queries, boolean incomplete) {
		return new FirebirdDomainReader(DialectHolder.defaultDialect25) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries[0]++;
				for (int i = 0; i < 50; i++) {
					try {
						if (queries[0] == 1)
							handler.handleResultSetNext(row(Map.of("RDB$FIELD_NAME", "domain_" + i, "RDB$FIELD_TYPE", 8,
									"RDB$FIELD_LENGTH", 4, "RDB$DIMENSIONS", 2)));
						else
							for (int dimension = 0; dimension < (incomplete ? 1 : 2); dimension++)
								handler.handleResultSetNext(row(Map.of("DOMAIN_NAME", "domain_" + i, "DIMENSION_NUMBER",
										dimension, "LOWER_BOUND", dimension == 0 ? -2 : 3, "UPPER_BOUND",
										dimension == 0 ? 2 : 4)));
					} catch (SQLException e) {
						throw new AssertionError(e);
					}
				}
				return null;
			}
		};
	}

	private static ExResultSet row(Map<String, Object> values) {
		return new ExResultSet(null) {
			@Override
			public String getString(String key) {
				return values.get(key) == null ? null : values.get(key).toString();
			}

			@Override
			public int getInt(String key) {
				return values.get(key) instanceof Number number ? number.intValue() : 0;
			}
		};
	}
}
