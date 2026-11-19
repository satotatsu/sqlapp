package com.sqlapp.data.db.dialect.mariadb;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class MariadbMergeRowAliasTest {
	@Test
	void usesOnlySupportedMergeSyntax() {
		for (int[] v : new int[][] { {10,5,0,0}, {11,4,0,0}, {11,8,0,0}, {12,1,0,0} }) {
			for (String tableName : new String[] {"merge_target", "sqlapp_new_row", "SQLAPP_NEW_ROW"}) {
				for (boolean coalesce : new boolean[] {false, true}) {
					var dialect = DialectResolver.getInstance().getDialect("mariadb", v[0], v[1], v[2]);
					var table = new Table(tableName);
					table.getColumns().add(new Column("id").setDataType(DataType.INT));
					table.getColumns().add(new Column("payload value").setDataType(DataType.VARCHAR).setLength(50));
					table.getColumns().add(new Column("lock_version").setDataType(DataType.INT));
					table.setPrimaryKey(table.getColumns().get("id"));
					SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(table, SqlType.MERGE);
					factory.getTableOptions().setWithCoalesceAtUpdate(coalesce);
					var operations = factory.createSql(table);
					assertEquals(1, operations.size());
					String sql = operations.getFirst().getSqlText();
					String update = sql.substring(sql.indexOf("ON DUPLICATE KEY"));
					if (v[3] == 1) {
						String alias = tableName.equalsIgnoreCase("sqlapp_new_row") ? "sqlapp_new_row_" : "sqlapp_new_row";
						assertTrue(sql.contains("AS " + alias), sql);
						assertTrue(update.contains(alias + ".sqlapp_value_1"), sql);
						assertFalse(update.contains("VALUES"), sql);
						assertTrue(update.contains("`payload value` ="), sql);
						if (coalesce) assertTrue(update.contains(alias + ".sqlapp_value_2"), sql);
					} else {
						assertFalse(sql.contains("AS sqlapp_new_row"), sql);
						assertTrue(update.contains("VALUES"), sql);
					}
				}
			}
		}
	}
}
