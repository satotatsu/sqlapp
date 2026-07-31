package com.sqlapp.data.db.dialect.postgres.sql;

import com.sqlapp.data.db.sql.DeleteFactory;
import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

public class Postgres180DeleteFactory extends DeleteFactory {
	@Override
	protected void addDeleteAfter(Table table, SqlSignature sqlSignature,
			AbstractSqlBuilder<?> builder) {
		Postgres180ReturningSupport.add(table, sqlSignature, getTableOptions(),
				builder);
	}
}
