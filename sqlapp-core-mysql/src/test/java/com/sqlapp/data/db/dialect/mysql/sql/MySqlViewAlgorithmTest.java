/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.View;
import com.sqlapp.data.db.dialect.mysql.util.MySqlDefiner;

class MySqlViewAlgorithmTest {
	@ParameterizedTest
	@CsvSource({"5,1", "5,5", "5,7", "8,0", "8,4", "9,0"})
	void allAlgorithmsComposeWithDefinerSecurityColumnsAndChecks(int major, int minor) {
		var registry = DialectResolver.getInstance().getDialect("MySql", major, minor, 0).createSqlFactoryRegistry();
		View view = new View("order view");
		view.setSchemaName("app");
		view.setStatement("SELECT id FROM orders");
		view.getColumns().add("order id");
		view.getSpecifics().put(MySqlDefiner.DEFINER, "'owner'@'localhost'");
		view.getSpecifics().put(MySqlCreateViewFactory.SECURITY_TYPE, "INVOKER");
		view.getSpecifics().put(MySqlCreateViewFactory.CHECK_OPTION, "LOCAL");
		SqlFactory<View> factory = registry.getSqlFactory(view, SqlType.CREATE);
		for (String algorithm : new String[] {"UNDEFINED", "MERGE", "TEMPTABLE"}) {
			view.getSpecifics().put(MySqlCreateViewFactory.ALGORITHM, " " + algorithm.toLowerCase(java.util.Locale.ROOT) + " ");
			view.getSpecifics().put(MySqlCreateViewFactory.CHECK_OPTION, "TEMPTABLE".equals(algorithm) ? "NONE" : "LOCAL");
			String sql = factory.createSql(view).get(0).getSqlText();
			assertTrue(sql.startsWith("CREATE ALGORITHM = " + algorithm + " DEFINER = `owner`@`localhost` SQL SECURITY INVOKER VIEW app.`order view`"), sql);
			assertTrue(sql.contains("`order id`"), sql);
			assertEquals(!"TEMPTABLE".equals(algorithm), sql.endsWith("WITH LOCAL CHECK OPTION"), sql);
			assertEquals(view.getSpecifics(), view.clone().getSpecifics());
		}
		view.getSpecifics().remove(MySqlCreateViewFactory.ALGORITHM);
		assertFalse(factory.createSql(view).get(0).getSqlText().contains("ALGORITHM"));
	}

	@Test
	void rejectsInvalidAlgorithmButCompleteDefinitionKeepsPrecedence() {
		View view = new View("v");
		view.setStatement("SELECT 1");
		SqlFactory<View> factory = DialectResolver.getInstance().getDialect("MySql", 8, 4, 0)
				.createSqlFactoryRegistry().getSqlFactory(view, SqlType.CREATE);
		for (String invalid : new String[] {"", " ", "AUTO", "MERGE; DROP VIEW v"}) {
			view.getSpecifics().put(MySqlCreateViewFactory.ALGORITHM, invalid);
			assertThrows(IllegalArgumentException.class, () -> factory.createSql(view));
		}
		view.getSpecifics().put(MySqlCreateViewFactory.ALGORITHM, "TEMPTABLE");
		view.getSpecifics().put(MySqlCreateViewFactory.CHECK_OPTION, "CASCADED");
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(view));
		view.setDefinition("CREATE ALGORITHM = TEMPTABLE VIEW v AS SELECT 2");
		assertEquals("CREATE ALGORITHM = TEMPTABLE VIEW v AS SELECT 2", factory.createSql(view).get(0).getSqlText());
	}
}
