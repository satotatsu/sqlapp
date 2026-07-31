package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

class Postgres170MergeSqlFactoryTest extends AbstractPostgresSqlFactoryTest {
	@Override
	protected int getMajorVersion() {
		return 17;
	}

	@Test
	void testWhenNotMatchedBySourceDelete() {
		Table table = table();
		SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, SqlType.MERGE);
		factory.getTableOptions().setMergeTableWithDelete(true);
		String sql = factory.createSql(table).get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.contains("WHEN NOT MATCHED BY SOURCE THEN DELETE"), sql);
	}

	@Test
	void testMergeReturningIsOptIn() {
		Table table = table();
		SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, SqlType.MERGE);
		String defaultSql = factory.createSql(table).get(0).getSqlText().replaceAll("\\s+", " ");
		assertFalse(defaultSql.contains("RETURNING"), defaultSql);

		factory.getTableOptions().setMergeTableWithReturning(true);
		String returningSql = factory.createSql(table).get(0).getSqlText()
				.replace("\"", "").replaceAll("\\s+", " ");
		assertTrue(returningSql.contains("RETURNING _target_.ID"), returningSql);
	}

	@Test
	void testPostgres16DoesNotEmitPostgres17Clause() {
		Table table = table();
		table.setDialect(DialectHolder.postgreSQL160);
		SqlFactory<Table> factory = DialectHolder.postgreSQL160.createSqlFactoryRegistry()
				.getSqlFactory(table, SqlType.MERGE);
		factory.getTableOptions().setMergeTableWithDelete(true);
		String sql = factory.createSql(table).get(0).getSqlText().replaceAll("\\s+", " ");
		assertFalse(sql.contains("BY SOURCE"), sql);
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
