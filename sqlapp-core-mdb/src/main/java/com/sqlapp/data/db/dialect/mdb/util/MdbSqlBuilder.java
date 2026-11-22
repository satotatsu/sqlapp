/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb.util;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.AbstractColumn;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.util.AbstractSqlBuilder;

/** SQL builder for Microsoft Access SQL. */
public class MdbSqlBuilder extends AbstractSqlBuilder<MdbSqlBuilder> {

	private static final long serialVersionUID = 1L;

	public MdbSqlBuilder(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected MdbSqlBuilder autoIncrement(final AbstractColumn<?> column) {
		return this;
	}

	@Override
	protected MdbSqlBuilder typeDefinition(final Column column) {
		if (!column.isIdentity() && column.getDataType() == DataType.UUID) {
			// UCanAccess 5.2.0 (Persist2Jet) creates every GUID column with
			// withAutoNumber(true), so a plain GUID cannot be created through SQL.
			throw new UnsupportedOperationException(
					"UCanAccess creates every GUID column as a Replication ID AutoNumber; use MdbJackcessSupport for a non-AutoNumber GUID column: "
							+ column.getName());
		}
		if (column.isIdentity()) {
			final DataType dataType = column.getDataType();
			if (dataType == DataType.UUID) {
				// Replication ID AutoNumber: UCanAccess creates GUID columns as
				// AutoNumber, so no extra clause is needed. The Access GUID length (38)
				// must not be passed on, or the length lookup falls back to TEXT(38).
				return _add(
						getDialect().getDbDataTypes().getDbType(DataType.UUID).getColumCreateDefinition(null, null));
			}
			if (dataType == null || isLongIntegerAutoNumber(dataType)) {
				return _add(getDialect().getIdentityColumnString());
			}
			throw new UnsupportedOperationException("Access AutoNumber supports only Long Integer or Replication ID: "
					+ column.getName() + " " + dataType);
		}
		return super.typeDefinition(column);
	}

	private static boolean isLongIntegerAutoNumber(final DataType dataType) {
		return switch (dataType) {
		case TINYINT, UTINYINT, SMALLINT, USMALLINT, INT, SMALLSERIAL, SERIAL -> true;
		default -> false;
		};
	}

	@Override
	public MdbSqlBuilder clone() {
		return (MdbSqlBuilder) super.clone();
	}
}
