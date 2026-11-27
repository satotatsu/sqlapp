/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.sql;

import com.sqlapp.data.db.dialect.hsql.util.HsqlSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateDomainFactory;
import com.sqlapp.data.schemas.Deferrability;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.util.CommonUtils;

/** Creates scalar domains with their named constraints. */
public class HsqlCreateDomainFactory extends AbstractCreateDomainFactory<HsqlSqlBuilder> {
	@Override
	protected boolean supportsDomainChecks() {
		return true;
	}

	@Override
	protected boolean supportsDomainDefinition() {
		return true;
	}

	@Override
	protected void addCreateObject(Domain obj, HsqlSqlBuilder builder) {
		if (!CommonUtils.isEmpty(obj.getDefinition())) {
			builder._add(obj.getDefinition());
			return;
		}
		builder.create().space()._add("DOMAIN").name(obj, getOptions().isDecorateSchemaName()).as().space();
		builder.typeDefinition(obj.getDataType(), obj.getDataTypeName(), obj.getLength(), obj.getScale());
		if (!CommonUtils.isEmpty(obj.getDefaultValue())) {
			builder.space()._add("DEFAULT ")._add(obj.getDefaultValue());
		}
		if (!CommonUtils.isEmpty(obj.getCheck())) {
			builder.space()._add("CHECK (")._add(obj.getCheck())._add(")");
		}
		if (obj.isNotNull()) {
			builder.space()._add("CHECK (VALUE IS NOT NULL)");
		}
		for (var check : obj.getChecks()) {
			if (!check.isEnable()
					|| check.getDeferrability() != null && check.getDeferrability() != Deferrability.NotDeferrable) {
				throw new IllegalArgumentException(
						"HSQLDB domain checks must be enabled and not deferrable: " + check.getName());
			}
			if (!CommonUtils.isEmpty(check.getName())) {
				builder.space()._add("CONSTRAINT").space().name(check.getName());
			}
			builder.space()._add("CHECK (")._add(check.getExpression())._add(")");
		}
	}
}
