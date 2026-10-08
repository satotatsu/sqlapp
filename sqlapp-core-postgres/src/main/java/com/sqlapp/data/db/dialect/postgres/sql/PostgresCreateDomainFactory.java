/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import static com.sqlapp.util.CommonUtils.isEmpty;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateDomainFactory;
import com.sqlapp.data.schemas.Domain;

/** Recreates PostgreSQL domains and enum types represented by Schema domains. */
public class PostgresCreateDomainFactory extends AbstractCreateDomainFactory<PostgresSqlBuilder> {
	@Override
	protected void addCreateObject(final Domain obj, final PostgresSqlBuilder builder) {
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
				if (!first) builder._add(", ");
				builder._add("'" + value.replace("'", "''") + "'");
				first = false;
			}
			builder._add(")");
		} else {
			builder.typeDefinition(obj.getDataType(), obj.getDataTypeName(), obj.getLength(), obj.getScale());
			if (obj.getArrayDimension() > 0) builder._add("[]".repeat(obj.getArrayDimension()));
			if (!isEmpty(obj.getDefaultValue())) builder.space()._add("DEFAULT ")._add(obj.getDefaultValue());
			if (obj.isNotNull()) builder.space()._add("NOT NULL");
			if (!isEmpty(obj.getCheck())) builder.space()._add("CHECK (")._add(obj.getCheck())._add(")");
		}
	}
}
