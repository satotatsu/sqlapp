/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.AbstractRefreshMviewFactory;
import com.sqlapp.data.schemas.Mview;

public class Postgres93RefreshMviewFactory extends AbstractRefreshMviewFactory<PostgresSqlBuilder> {
	@Override
	protected void addRefreshObject(Mview view, PostgresSqlBuilder builder) {
		builder.refresh().materialized().view().space().name(view, getOptions().isDecorateSchemaName());
	}
}
