/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-informix.
 */
package com.sqlapp.data.db.dialect.informix.metadata;

import java.sql.SQLException;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcColumnReader;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.jdbc.ExResultSet;

/** Normalizes Informix SERIAL family columns as generated identities. */
public class InformixColumnReader extends JdbcColumnReader {
	public InformixColumnReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected Column createColumn(final ExResultSet resultSet) throws SQLException {
		Column column = super.createColumn(resultSet);
		String typeName = getString(resultSet, "TYPE_NAME");
		if ("SERIAL".equalsIgnoreCase(typeName)) {
			column.setDataType(DataType.SERIAL);
			column.setIdentity(true);
			column.setIdentityGenerationType(IdentityGenerationType.ByDefault);
			column.setDefaultValue(null);
		} else if ("SERIAL8".equalsIgnoreCase(typeName)
				|| "BIGSERIAL".equalsIgnoreCase(typeName)) {
			column.setDataType(DataType.BIGSERIAL);
			column.setIdentity(true);
			column.setIdentityGenerationType(IdentityGenerationType.ByDefault);
			column.setDefaultValue(null);
		}
		return column;
	}
}
