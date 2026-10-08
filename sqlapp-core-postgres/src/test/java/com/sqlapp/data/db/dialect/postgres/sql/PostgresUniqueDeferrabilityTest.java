package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.*;

class PostgresUniqueDeferrabilityTest {
	@Test
	void preservesPrimaryAndUniqueDeferrabilityFrom90() {
		for (int major : new int[] {8, 9, 11, 15, 18}) {
			var registry = DialectResolver.getInstance().getDialect("postgres", major, 0, null).createSqlFactoryRegistry();
			for (boolean primary : new boolean[] {true, false}) {
				Table table = new Table("items");
				table.getColumns().add("id", c -> c.setDataType(DataType.INT));
				var key = new UniqueConstraint("key", primary);
				key.getColumns().add(table.getColumns().get("id"));
				table.getConstraints().add(key);
				for (var mode : Deferrability.values()) {
					key.setDeferrability(mode);
					for (String sql : java.util.List.of(
							registry.createSql(key, SqlType.CREATE).get(0).getSqlText(),
							registry.createSql(table, SqlType.CREATE).get(0).getSqlText())) {
						if (major >= 9 && mode != Deferrability.NotDeferrable)
							assertTrue(sql.contains("DEFERRABLE " + mode.getSqlValue()), sql);
						else assertFalse(sql.contains("DEFERRABLE"), sql);
					}
				}
			}
		}
	}
}
