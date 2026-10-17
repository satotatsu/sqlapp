/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.schemas.PublicSynonym;

public class OracleDropPublicSynonymFactory extends AbstractDropNamedObjectFactory<PublicSynonym, OracleSqlBuilder> {
	@Override
	protected void addDropObject(PublicSynonym synonym, OracleSqlBuilder builder) {
		builder.drop().public_().synonym().space().name(synonym);
	}
}
