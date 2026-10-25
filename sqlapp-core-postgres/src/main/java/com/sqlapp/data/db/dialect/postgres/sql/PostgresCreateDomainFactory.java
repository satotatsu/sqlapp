/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.List;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateDomainFactory;
import com.sqlapp.data.schemas.Domain;

/**
 * Recreates PostgreSQL domains and enum types represented by Schema domains.
 */
public class PostgresCreateDomainFactory extends AbstractCreateDomainFactory<PostgresSqlBuilder> {
	@Override
	protected boolean supportsDomainChecks() {
		return true;
	}

	@Override
	protected void addCreateObject(final Domain obj, final PostgresSqlBuilder builder) {
		if (obj.getDataType() == DataType.ENUM && !obj.getChecks().isEmpty()) {
			throw new IllegalArgumentException("PostgreSQL enum types cannot have domain checks: " + obj.getName());
		}
		if (!isEmpty(obj.getDefinition())) {
			builder._add(obj.getDefinition());
			return;
		}
		builder.create().space()._add(obj.getDataType() == DataType.ENUM ? "TYPE" : "DOMAIN");
		builder.name(obj, getOptions().isDecorateSchemaName()).as().space();
		if (obj.getDataType() == DataType.ENUM) {
			builder._add("ENUM (");
			boolean first = true;
			for (String value : obj.getValues()) {
				if (!first)
					builder._add(", ");
				builder._add("'" + value.replace("'", "''") + "'");
				first = false;
			}
			builder._add(")");
		} else {
			if (obj.getDataType() == DataType.OTHER && !isEmpty(obj.getDataTypeName())) {
				// Catalog format_type preserves qualified, quoted user-defined type names.
				builder._add(obj.getDataTypeName());
			} else {
				builder.typeDefinition(obj.getDataType(), obj.getDataTypeName(), obj.getLength(), obj.getScale());
			}
			if (obj.getArrayDimension() > 0)
				builder._add("[]".repeat(obj.getArrayDimension()));
			if (!isEmpty(obj.getDefaultValue()))
				builder.space()._add("DEFAULT ")._add(obj.getDefaultValue());
			if (obj.isNotNull())
				builder.space()._add("NOT NULL");
			if (!isEmpty(obj.getCheck()))
				builder.space()._add("CHECK (")._add(obj.getCheck())._add(")");
			for (var check : obj.getChecks()) {
				if (!check.isEnable() || check.getDeferrability() != null
						&& check.getDeferrability() != com.sqlapp.data.schemas.Deferrability.NotDeferrable) {
					throw new IllegalArgumentException(
							"PostgreSQL domain checks must be enabled and not deferrable: " + check.getName());
				}
				if (!isEmpty(check.getName()))
					builder.space()._add("CONSTRAINT").space().name(check.getName());
				builder.space()._add("CHECK (")._add(check.getExpression())._add(")");
			}
		}
	}

	@Override
	protected void addOptions(final Domain obj, List<SqlOperation> sqlList) {
		if (obj.getRemarks() == null)
			return;
		PostgresSqlBuilder builder = createSqlBuilder();
		builder.comment().on().space()._add(obj.getDataType() == DataType.ENUM ? "TYPE" : "DOMAIN");
		builder.space().name(obj, getOptions().isDecorateSchemaName()).is().sqlChar(obj.getRemarks());
		addSql(sqlList, builder, SqlType.SET_COMMENT, obj);
	}

}
