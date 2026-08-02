/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb.metadata;

import java.sql.Connection;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcPrimaryKeyConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.UniqueConstraint;

/** Marks constraints returned by UCanAccess primary-key metadata as primary keys. */
public class MdbPrimaryKeyConstraintReader
		extends JdbcPrimaryKeyConstraintReader {

	public MdbPrimaryKeyConstraintReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<UniqueConstraint> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final List<UniqueConstraint> constraints = super.doGetAll(connection,
				context, productVersionInfo);
		constraints.forEach(constraint -> constraint.setPrimaryKey(true));
		return constraints;
	}
}
