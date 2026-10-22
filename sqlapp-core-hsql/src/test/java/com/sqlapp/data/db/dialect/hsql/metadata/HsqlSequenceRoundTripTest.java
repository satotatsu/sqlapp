/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;

class HsqlSequenceRoundTripTest {
	@Test
	void preservesCycleAndBoundsWhenRecreated() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:sequence-audit", "SA", "");
				var statement = connection.createStatement()) {
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = dialect.getCatalogReader().getSchemaReader().getSequenceReader();
			reader.setSchemaName("PUBLIC");
			for (boolean cycle : new boolean[] { true, false }) {
				statement.execute("CREATE SEQUENCE AUDIT_SEQ START WITH 50 INCREMENT BY 5 MINVALUE 5 MAXVALUE 1000 "
						+ (cycle ? "CYCLE" : "NO CYCLE") + "");
				var sequence = reader.getAll(connection).get(0);
				assertEquals(cycle, sequence.isCycle());
				assertEquals(5, sequence.getMinValue().intValue());
				assertEquals(1000, sequence.getMaxValue().intValue());
				var ddl = dialect.createSqlFactoryRegistry().createSql(sequence, SqlType.CREATE);
				statement.execute("DROP SEQUENCE AUDIT_SEQ");
				for (var sql : ddl) {
					statement.execute(sql.getSqlText());
				}
				var recreated = reader.getAll(connection).get(0);
				assertEquals(sequence.getStartValue(), recreated.getStartValue());
				assertEquals(sequence.getMinValue(), recreated.getMinValue());
				assertEquals(sequence.getMaxValue(), recreated.getMaxValue());
				assertEquals(sequence.getIncrementBy(), recreated.getIncrementBy());
				assertEquals(cycle, recreated.isCycle());
				statement.execute("DROP SEQUENCE AUDIT_SEQ");
			}
		}
	}
}
