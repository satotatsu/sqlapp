package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

class Postgres180MergeSqlFactoryTest extends AbstractPostgresSqlFactoryTest {
	@Override
	protected int getMajorVersion() {
		return 18;
	}

	@Test
	void testReturningOldAndNewAliases() {
		Table table = table();
		SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, SqlType.MERGE);
		factory.getTableOptions().setMergeTableWithReturning(true);
		factory.getTableOptions().setReturningOldAlias("old_row");
		factory.getTableOptions().setReturningNewAlias("new_row");
		String sql = factory.createSql(table).get(0).getSqlText()
				.replace("\"", "").replaceAll("\\s+", " ").replace(" )", ")");
		assertTrue(sql.contains("RETURNING WITH (OLD AS old_row, NEW AS new_row)"), sql);
		assertTrue(sql.contains("old_row.ID AS old_row_ID"), sql);
		assertTrue(sql.contains("new_row.ID AS new_row_ID"), sql);
	}

	@Test
	void testRejectSameOldAndNewAlias() {
		Table table = table();
		SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, SqlType.MERGE);
		factory.getTableOptions().setMergeTableWithReturning(true);
		factory.getTableOptions().setReturningOldAlias("row_value");
		factory.getTableOptions().setReturningNewAlias("ROW_VALUE");
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(table));
	}

	private Table table() {
		Table table = new Table("TARGET_TABLE");
		table.setDialect(dialect);
		table.getColumns().add("ID", c -> c.setDataType(DataType.INT));
		table.getColumns().add("VALUE", c -> c.setDataType(DataType.VARCHAR).setLength(20));
		table.setPrimaryKey(table.getColumns().get("ID"));
		return table;
	}
}
