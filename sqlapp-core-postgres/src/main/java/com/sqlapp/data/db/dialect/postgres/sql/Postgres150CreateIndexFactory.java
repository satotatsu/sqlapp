package com.sqlapp.data.db.dialect.postgres.sql;

import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;

public class Postgres150CreateIndexFactory extends Postgres110CreateIndexFactory {
	@Override
	protected void addUnique(Index obj, Table table, PostgresSqlBuilder builder) {
		builder.unique(obj.isUnique());
		if (obj.isUnique() && Boolean.parseBoolean(obj.getSpecifics().get(NULLS_NOT_DISTINCT))) {
			builder.space()._add("NULLS NOT DISTINCT").space();
		}
		builder.index();
		boolean concurrent = table != null && getTableOptions().getOnlineIndex().test(table, obj);
		builder.concurrently(concurrent);
		builder.ifNotExists(table != null && getOptions().isCreateIfNotExists()).space();
	}
}
