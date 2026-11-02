/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class MariadbPeriodSqlTest {
	private Table table(TemporalPeriodType type) {
		Table table = new Table("history");
		table.getColumns().add("begin", c -> c.setDataType(DataType.TIMESTAMP));
		table.getColumns().add("end", c -> c.setDataType(DataType.TIMESTAMP));
		table.getTemporalPeriods().add(new TemporalPeriod("valid time").setPeriodType(type).setStartColumnName("begin")
				.setEndColumnName("end"));
		return table;
	}

	private String sql(Table table) {
		return DialectResolver.getInstance().getDialect("MariaDB", 11, 5, 0).createSqlFactoryRegistry()
				.createSql(table, SqlType.CREATE).get(0).getSqlText().replaceAll("\\s+", " ");
	}

	@Test
	void systemTimeIsKeywordAndApplicationTimeIsIdentifierThroughXml() throws Exception {
		Table system = table(TemporalPeriodType.SYSTEM_TIME);
		system.setSystemVersioning(new SystemVersioning());
		Table restored = SchemaUtils.readXml(new java.io.StringReader(system.asXml()));
		assertTrue(sql(restored).contains("PERIOD FOR SYSTEM_TIME ("), sql(restored));
		Table application = table(TemporalPeriodType.APPLICATION_TIME);
		assertTrue(sql(application).contains("PERIOD FOR `valid time` ("), sql(application));
	}

	@Test
	void incompleteMissingOrRepeatedColumnsFailClearly() {
		Table table = table(TemporalPeriodType.APPLICATION_TIME);
		TemporalPeriod period = table.getTemporalPeriods().get(0);
		period.setEndColumnName(null);
		assertThrows(IllegalArgumentException.class, () -> sql(table));
		period.setEndColumnName("missing");
		assertThrows(IllegalArgumentException.class, () -> sql(table));
		period.setEndColumnName("begin");
		assertThrows(IllegalArgumentException.class, () -> sql(table));
	}
}
