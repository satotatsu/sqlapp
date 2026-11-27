/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.sql;

import com.sqlapp.data.db.dialect.hsql.util.HsqlSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateSynonymFactory;
import com.sqlapp.data.schemas.Synonym;
import com.sqlapp.util.CommonUtils;

public class HsqlCreateSynonymFactory extends AbstractCreateSynonymFactory<HsqlSqlBuilder> {
	@Override
	protected void addCreateObject(Synonym obj, HsqlSqlBuilder builder) {
		if (!CommonUtils.isEmpty(obj.getDefinition())) {
			builder._add(obj.getDefinition());
			return;
		}
		if (CommonUtils.isEmpty(obj.getObjectName()) || !CommonUtils.isEmpty(obj.getDbLinkName())) {
			throw new IllegalArgumentException("HSQLDB synonym requires a local target object: " + obj.getName());
		}
		builder.create().space()._add("SYNONYM").name(obj, getOptions().isDecorateSchemaName()).space()._add("FOR")
				.space();
		if (obj.getObjectSchemaName() != null)
			builder.name(obj.getObjectSchemaName())._add(".");
		builder.name(obj.getObjectName());
	}
}
