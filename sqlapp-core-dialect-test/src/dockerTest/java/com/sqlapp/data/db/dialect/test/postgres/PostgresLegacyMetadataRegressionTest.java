/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.postgres;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.sqlapp.data.db.dialect.DialectResolver;
class PostgresLegacyMetadataRegressionTest {
	@ParameterizedTest
	@ValueSource(strings={"postgres:9.6.24", "postgres:10.23-bullseye"})
	void preservesSequenceAndAdvancedIndexMetadataAcross10Boundary(String image) throws Exception {
		try (var db = new PostgreSQLContainer(image)) {
			db.start();
			try (var c = db.createConnection("")) {
				PostgresMetadataRegressionAssertions.verify(c, DialectResolver.getInstance().getDialect(c));
			}
		}
	}
}
