package com.sqlapp.data.db.dialect.postgres.sql;

import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;

public class Postgres150CreateIndexFactory extends Postgres110CreateIndexFactory {
	@Override
	protected void addIncludesAfter(Index obj, Table table, PostgresSqlBuilder builder) {
		if (obj.isUnique() && Boolean.parseBoolean(obj.getSpecifics().get(NULLS_NOT_DISTINCT))) {
			builder.space()._add("NULLS NOT DISTINCT");
		}
	}
}
