package com.sqlapp.data.db.command.generator.factory;

import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.data.schemas.Table;

public class InsertSqlFactory {

	public List<SqlOperation> createSql(Table table, SqlType sqlType, Dialect dialect, TableOptions option) {
		final SqlFactoryRegistry sqlFactoryRegistry = dialect.createSqlFactoryRegistry();
		sqlFactoryRegistry.getOption().setTableOptions(option);
		final SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, sqlType);
		final List<SqlOperation> operations = factory.createSql(table);
		return operations;
	}

	public String toText(List<SqlOperation> operations) {
		StringBuilder builder = new StringBuilder();
		long cnt = operations.stream().filter(o -> !o.getSqlType().isComment() && !o.getSqlType().isEmptyLine())
				.count();
		if (cnt > 1) {

		} else {

		}
		return builder.toString();
	}

}
