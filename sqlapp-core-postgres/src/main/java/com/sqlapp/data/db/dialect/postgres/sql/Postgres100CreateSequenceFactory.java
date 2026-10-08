/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Sequence;

/** PostgreSQL 10 introduced typed sequences. */
public class Postgres100CreateSequenceFactory extends PostgresCreateSequenceFactory {
	@Override
	protected void addDataType(final Sequence obj, PostgresSqlBuilder builder) {
		DataType type = obj.getDataType();
		if (type == null && obj.getDataTypeName() != null) {
			Column column = new Column();
			if (getDialect().setDbType(obj.getDataTypeName(), null, null, column)) type = column.getDataType();
		}
		if (type == DataType.SMALLINT || type == DataType.INT || type == DataType.BIGINT) {
			builder.as().space().typeDefinition(type, null, null, null);
		}
	}
}
