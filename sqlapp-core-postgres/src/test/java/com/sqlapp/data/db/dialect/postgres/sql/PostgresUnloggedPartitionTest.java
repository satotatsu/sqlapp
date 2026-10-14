package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.Table;

class PostgresUnloggedPartitionTest {
	@Test
	void rejectsUnloggedParentsWithoutChangingOrdinaryTables() {
		for (int major : new int[] { 10, 14, 17, 18 }) {
			var registry = DialectResolver.getInstance().getDialect("postgres", major, 0, null)
					.createSqlFactoryRegistry();
			Table table = new Table("events").setUnlogged(true);
			table.getColumns().add("id", c -> c.setDataType(DataType.INT));
			SqlFactory<Table> factory = registry.getSqlFactory(table, SqlType.CREATE);
			assertTrue(factory.createSql(table).get(0).getSqlText().contains("UNLOGGED"));
			table.setPartitioning(new Partitioning().setPartitioningType(PartitioningType.Range));
			table.getPartitioning().getPartitioningColumns().add("id");
			assertThrows(IllegalArgumentException.class, () -> factory.createSql(table));
			assertTrue(table.isUnlogged());
			table.setUnlogged(false);
			assertDoesNotThrow(() -> factory.createSql(table));
		}
	}
}
