/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.schemas.Domain;

/**
 * DROP factory for Oracle Database 23ai data use case domains.
 */
public class Oracle23aiDropDomainFactory extends OracleDropDomainFactory {

	@Override
	protected void addDropObject(final Domain domain,
			final OracleSqlBuilder builder) {
		builder.drop().space()._add("DOMAIN").space();
		builder.ifExists(this.getOptions().isDropIfExists()).space();
		builder.name(domain, this.getOptions().isDecorateSchemaName());
	}
}
