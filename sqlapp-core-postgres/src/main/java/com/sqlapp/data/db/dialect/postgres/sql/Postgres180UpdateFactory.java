package com.sqlapp.data.db.dialect.postgres.sql;

import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.db.sql.UpdateFactory;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

public class Postgres180UpdateFactory extends UpdateFactory {
	@Override
	protected void addUpdateAfter(Table table, SqlSignature sqlSignature,
			AbstractSqlBuilder<?> builder) {
		Postgres180ReturningSupport.add(table, sqlSignature, getTableOptions(),
				builder);
	}
}
