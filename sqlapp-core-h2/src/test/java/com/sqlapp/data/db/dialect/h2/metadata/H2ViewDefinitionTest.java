/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.SQLException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.View;

class H2ViewDefinitionTest {
	@ParameterizedTest
	@ValueSource(strings = { "CREATE VIEW V(X) AS SELECT 1", "  create\nforce view V(X) as\nSELECT 1" })
	void preservesCompleteLegacyCatalogDdl(String definition) throws Exception {
		var view = new View("V");
		H2ViewReader.setViewDefinition(view, definition);
		assertFalse(view.getDefinition().isEmpty());
		assertTrue(view.getStatement().isEmpty());
		for (var dialect : java.util.List.of(DialectResolver.getInstance().getDialect("H2", 1, 4, 200),
				DialectResolver.getInstance().getDialect("H2", 2, 3, 232)))
			assertEquals(definition,
					dialect.createSqlFactoryRegistry().createSql(view, SqlType.CREATE).get(0).getSqlText());
	}

	@ParameterizedTest
	@ValueSource(strings = { "SELECT 1", "WITH Q AS (SELECT 1 AS X) SELECT X FROM Q",
			"SELECT 'CREATE VIEW V AS SELECT 1'" })
	void keepsQueryBodiesInStatement(String body) throws Exception {
		var view = new View("V");
		H2ViewReader.setViewDefinition(view, body);
		assertTrue(view.getDefinition().isEmpty());
		assertEquals(body, String.join("\n", view.getStatement()));
	}

	@Test
	void unavailableBodiesFailClearly() {
		assertThrows(SQLException.class, () -> H2ViewReader.setViewDefinition(new View("V"), null));
		assertThrows(SQLException.class, () -> H2ViewReader.setViewDefinition(new View("V"), " "));
	}
}
