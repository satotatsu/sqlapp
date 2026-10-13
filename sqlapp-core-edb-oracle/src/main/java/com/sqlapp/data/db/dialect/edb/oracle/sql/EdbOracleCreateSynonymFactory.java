/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle.sql;
import com.sqlapp.data.db.sql.AbstractCreateSynonymFactory;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.schemas.Synonym;

public class EdbOracleCreateSynonymFactory extends AbstractCreateSynonymFactory<PostgresSqlBuilder> {
	@Override protected void addCreateObject(Synonym object,PostgresSqlBuilder builder) {
		if(object.getObjectName()==null || object.getObjectName().isBlank()) throw new IllegalArgumentException("EPAS synonym requires a target object name");
		builder.create().or().replace().synonym().name(object,true).for_().space().names(object.getObjectSchemaName(),object.getObjectName());
		if(object.getDbLinkName()!=null && !object.getDbLinkName().isBlank()) builder._add('@')._add(getDialect().quote(object.getDbLinkName()));
	}
}
