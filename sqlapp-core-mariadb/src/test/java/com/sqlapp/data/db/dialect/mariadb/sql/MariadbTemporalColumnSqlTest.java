/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.*;

class MariadbTemporalColumnSqlTest {
	@Test
	void createAndAlterPreserveSystemPeriodRolesAfterXml() throws Exception {
		for (int[] version : new int[][] { { 10, 3 }, { 10, 11 }, { 11, 4 }, { 11, 5 }, { 12, 1 } }) {
			Table table = new Table("history");
			table.getColumns().add("started", c -> c.setDataType(DataType.TIMESTAMP).setScale(6));
			table.getColumns().add("ended", c -> c.setDataType(DataType.TIMESTAMP).setScale(6));
			table.getColumns().add("ordinary", c -> c.setDataType(DataType.INT));
			table.getTemporalPeriods()
					.add(new TemporalPeriod("SYSTEM_TIME").setPeriodType(TemporalPeriodType.SYSTEM_TIME)
							.setStartColumnName("started").setEndColumnName("ended"));
			Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			var dialect = DialectResolver.getInstance().getDialect("MariaDB", version[0], version[1], 0);
			for (String name : new String[] { "started", "ended", "ordinary" }) {
				Column column = restored.getColumns().get(name);
				String create = dialect.createSqlBuilder().definition(column, true).toString();
				String alter = dialect.createSqlBuilder().definitionForAlterColumn(column).toString();
				String role = name.equals("started") ? "ROW START" : "ROW END";
				if (name.equals("ordinary")) {
					assertFalse(alter.contains("GENERATED ALWAYS AS ROW"), alter);
				} else {
					assertTrue(create.contains("GENERATED ALWAYS AS " + role), create);
					assertTrue(alter.contains("GENERATED ALWAYS AS " + role), alter);
					assertEquals(alter.indexOf("GENERATED ALWAYS AS"), alter.lastIndexOf("GENERATED ALWAYS AS"));
				}
			}
		}
	}
}
