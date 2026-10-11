package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.*;

class PostgresCoveringConstraintTest {
	@Test
	void rendersCoveringAndNullOptionsAtVersionBoundaries() {
		for (int major : new int[] { 9, 10, 11, 14, 15, 18 }) {
			var registry = DialectResolver.getInstance().getDialect("postgres", major, 0, null)
					.createSqlFactoryRegistry();
			for (boolean primary : new boolean[] { true, false }) {
				Table table = new Table("items");
				table.getColumns().add("id", c -> c.setDataType(DataType.INT));
				table.getColumns().add("Payload Value", c -> c.setDataType(DataType.INT));
				var key = new UniqueConstraint("key", primary);
				key.getColumns().add(table.getColumns().get("id"));
				key.getIndex().getIncludes().add("Payload Value");
				key.getSpecifics().put("nullsNotDistinct", "true");
				key.setDeferrability(Deferrability.InitiallyDeferred);
				table.getConstraints().add(key);
				for (String sql : java.util.List.of(registry.createSql(key, SqlType.CREATE).get(0).getSqlText(),
						registry.createSql(table, SqlType.CREATE).get(0).getSqlText())) {
					assertEquals(major >= 11, sql.contains("INCLUDE"), sql);
					assertEquals(major >= 15 && !primary, sql.contains("NULLS NOT DISTINCT"), sql);
					if (major >= 11) {
						assertTrue(sql.contains("\"Payload Value\""), sql);
						assertTrue(sql.indexOf("INCLUDE") < sql.indexOf("DEFERRABLE"), sql);
					}
				}
			}
		}
	}
}
