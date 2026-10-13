/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle.sql;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.schemas.Synonym;
public class EdbOracleDropSynonymFactory extends AbstractDropNamedObjectFactory<Synonym,PostgresSqlBuilder> {
	@Override protected void addDropObject(Synonym object,PostgresSqlBuilder builder) {builder.drop().synonym().name(object,true);}
}
