/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb.sql;

import com.sqlapp.data.db.dialect.mdb.util.MdbSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateForeignKeyConstraintFactory;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Table;

/**
 * Keeps non-enforced Access relationships from becoming enforced JDBC
 * constraints.
 */
public class MdbCreateForeignKeyConstraintFactory extends AbstractCreateForeignKeyConstraintFactory<MdbSqlBuilder> {

	@Override
	public void addObjectDetail(final ForeignKeyConstraint key, final Table table, final MdbSqlBuilder builder) {
		if (!key.isEnable()) {
			throw new UnsupportedOperationException("UCanAccess cannot create a non-enforced Access relationship: "
					+ key.getName() + ". Use MdbJackcessSupport.addRelationship or rebuild instead.");
		}
		super.addObjectDetail(key, table, builder);
	}
}
