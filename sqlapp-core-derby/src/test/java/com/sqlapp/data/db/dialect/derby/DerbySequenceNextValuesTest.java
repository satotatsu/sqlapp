/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-derby.
 */
package com.sqlapp.data.db.dialect.derby;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Verifies bulk sequence preallocation on Derby. */
class DerbySequenceNextValuesTest {

	@Test
	void testSequenceValuesAreAllocatedInOneQuery() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:derby:memory:sequence-next-values;create=true")) {
			connection.createStatement().execute("CREATE SEQUENCE seq1 AS BIGINT START WITH 3 INCREMENT BY 4");
			Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			Sequence sequence = new Sequence("SEQ1");
			SqlNode node = dialect.createSqlFactoryRegistry()
					.createSqlNodes(sequence, SqlType.SEQUENCE_NEXT_VALUES).get(0);
			SqlParameterCollection parameters = node.eval(5);
			try (PreparedStatement statement = parameters.createStatement(connection)) {
				parameters.setBind(statement);
				try (ResultSet resultSet = statement.executeQuery()) {
					for (int i = 0; i < 5; i++) {
						resultSet.next();
						assertEquals(3 + 4 * i, resultSet.getLong(1));
					}
				}
			}
		}
	}
}
