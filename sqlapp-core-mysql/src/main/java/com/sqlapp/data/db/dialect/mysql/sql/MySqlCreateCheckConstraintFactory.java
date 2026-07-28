/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 */
package com.sqlapp.data.db.dialect.mysql.sql;

import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateCheckConstraintFactory;
import com.sqlapp.data.schemas.CheckConstraint;

/**
 * MySQL check-constraint SQL generator.
 */
public class MySqlCreateCheckConstraintFactory extends AbstractCreateCheckConstraintFactory<MySqlSqlBuilder> {

	@Override
	protected void addCheckConstraintAfter(CheckConstraint constraint, MySqlSqlBuilder builder) {
		if (!constraint.isEnable() && "MySQL".equalsIgnoreCase(getDialect().getProductName())) {
			builder.space().not().space()._add("ENFORCED");
		}
	}
}
