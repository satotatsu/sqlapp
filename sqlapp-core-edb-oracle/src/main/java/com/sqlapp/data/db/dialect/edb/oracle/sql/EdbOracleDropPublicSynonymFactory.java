/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle.sql;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.schemas.PublicSynonym;
public class EdbOracleDropPublicSynonymFactory extends AbstractDropNamedObjectFactory<PublicSynonym,PostgresSqlBuilder> {
	@Override protected void addDropObject(PublicSynonym object,PostgresSqlBuilder builder) {builder.drop().space()._add("PUBLIC").synonym().name(object);}
}
