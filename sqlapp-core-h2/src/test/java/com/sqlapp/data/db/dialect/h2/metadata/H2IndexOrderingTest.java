/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import static com.sqlapp.data.db.dialect.h2.metadata.H2DialectRoundTripTest.*;
import java.sql.DriverManager;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class H2IndexOrderingTest {
	static Stream<Arguments> cases() {
		return Stream.of("PUBLIC", "odd\" schema").flatMap(schema -> Stream.of("ASC", "DESC")
				.flatMap(order -> Stream.of("FIRST", "LAST").map(nulls -> Arguments.of(schema, order, nulls))));
	}

	@ParameterizedTest
	@MethodSource("cases")
	void preservesIndexDirectionAndNullOrdering(String schema, String order, String nulls) throws Exception {
		try (var source = DriverManager.getConnection("jdbc:h2:mem:");
				var target = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = source.createStatement();
				var destination = target.createStatement()) {
			if (!schema.equals("PUBLIC")) {
				statement.execute("CREATE SCHEMA " + quote(schema));
				destination.execute("CREATE SCHEMA " + quote(schema));
			}
			String prefix = quote(schema) + ".";
			statement.execute("CREATE TABLE " + prefix + "T(C INT)");
			statement.execute("CREATE INDEX " + prefix + "IX ON " + prefix + "T(C " + order + " NULLS " + nulls + ")");
			var table = read(source, schema).getTables().get("T");
			create(target, table);
			var expected = table.getIndexes().get("IX").getColumns().get(0);
			var actual = read(target, schema).getTables().get("T").getIndexes().get("IX").getColumns().get(0);
			assertNotNull(expected.getNullsOrder());
			assertEquals(expected.getNullsOrder(), actual.getNullsOrder());
			assertEquals(expected.getOrder(), actual.getOrder());
		}
	}
}
