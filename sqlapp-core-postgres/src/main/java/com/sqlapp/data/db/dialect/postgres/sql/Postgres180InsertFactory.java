package com.sqlapp.data.db.dialect.postgres.sql;

import com.sqlapp.data.db.sql.InsertFactory;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

public class Postgres180InsertFactory extends InsertFactory {
	@Override
	protected void addInsertAfter(Table table, AbstractSqlBuilder<?> builder) {
		Postgres180ReturningSupport.add(table, createSqlSignature(table),
				getTableOptions(), builder);
	}
}
