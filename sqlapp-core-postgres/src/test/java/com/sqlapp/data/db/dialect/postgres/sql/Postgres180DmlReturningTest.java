package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

class Postgres180DmlReturningTest extends AbstractPostgresSqlFactoryTest {
	@Override
	protected int getMajorVersion() {
		return 18;
	}

	@Test
	void testInsertUpdateDeleteReturningOldAndNew() {
		Table table = table();
		for (SqlType type : new SqlType[] {
				SqlType.INSERT, SqlType.UPDATE, SqlType.DELETE }) {
			SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, type);
			factory.getTableOptions().setReturningOldAlias("old_row");
			factory.getTableOptions().setReturningNewAlias("new_row");
			String sql = factory.createSql(table).get(0).getSqlText()
					.replace("\"", "").replaceAll("\\s+", " ").replace(" )", ")");
			assertTrue(sql.contains(
					"RETURNING WITH (OLD AS old_row, NEW AS new_row)"), sql);
			assertTrue(sql.contains("old_row.ID AS old_row_ID"), sql);
			assertTrue(sql.contains("new_row.ID AS new_row_ID"), sql);
		}
	}

	@Test
	void testAliasesDoNotChangePostgres17Dml() {
		Table table = table();
		SqlFactory<Table> factory = com.sqlapp.data.db.dialect.postgres.DialectHolder
				.postgreSQL170.createSqlFactoryRegistry()
				.getSqlFactory(table, SqlType.UPDATE);
		factory.getTableOptions().setReturningOldAlias("old_row");
		factory.getTableOptions().setReturningNewAlias("new_row");
		String sql = factory.createSql(table).get(0).getSqlText();
		assertFalse(sql.contains("RETURNING WITH"), sql);
	}

	private Table table() {
		Table table = new Table("ORDERS");
		table.setDialect(dialect);
		table.getColumns().add("ID", c -> c.setDataType(DataType.INT));
		table.getColumns().add("NAME",
				c -> c.setDataType(DataType.VARCHAR).setLength(20));
		table.setPrimaryKey(table.getColumns().get("ID"));
		return table;
	}
}
