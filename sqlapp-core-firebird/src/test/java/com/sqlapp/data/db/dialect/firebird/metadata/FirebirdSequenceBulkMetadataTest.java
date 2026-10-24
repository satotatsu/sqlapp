/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class FirebirdSequenceBulkMetadataTest {
	@Test
	void currentValuesAreBatchedAndIdentifiersAreQuoted() throws Exception {
		List<String> queries = new ArrayList<>();
		var reader = new FirebirdSequenceReader(DialectHolder.defaultDialect25) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				var sql = new SqlParameterCollection();
				node.eval(context, sql);
				queries.add(sql.getSql());
				try {
					handler.handleResultSetNext(new ExResultSet(null) {
						@Override
						public BigDecimal getBigDecimal(String key) {
							return BigDecimal.valueOf(123);
						}
					});
				} catch (SQLException e) {
					throw new AssertionError(e);
				}
				return null;
			}
		};
		List<Sequence> sequences = new ArrayList<>();
		for (int i = 0; i < 550; i++)
			sequences.add(new Sequence("sequence \"" + i));
		reader.setMetadataDetail(null, new ParametersContext(), sequences);
		for (var sequence : sequences)
			reader.setMetadataDetail(null, sequence);
		assertEquals(2, queries.size());
		assertTrue(queries.get(0).contains("GEN_ID(\"sequence \"\"0\",0)"), queries.get(0));
		assertTrue(sequences.stream()
				.allMatch(sequence -> java.math.BigInteger.valueOf(123).equals(sequence.getLastValue())));
	}

	@Test
	void emptySequenceSetDoesNotQueryAndMissingValuesFail() throws Exception {
		var reader = new FirebirdSequenceReader(DialectHolder.defaultDialect25) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				return null;
			}
		};
		reader.setMetadataDetail(null, new ParametersContext(), List.of());
		assertThrows(SQLException.class,
				() -> reader.setMetadataDetail(null, new ParametersContext(), List.of(new Sequence("missing"))));
	}
}
