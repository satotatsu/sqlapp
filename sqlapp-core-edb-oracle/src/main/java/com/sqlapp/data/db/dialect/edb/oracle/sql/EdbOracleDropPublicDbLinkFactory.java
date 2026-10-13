/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle.sql;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.schemas.PublicDbLink;
public class EdbOracleDropPublicDbLinkFactory extends AbstractDropNamedObjectFactory<PublicDbLink,PostgresSqlBuilder> {
	@Override protected void addDropObject(PublicDbLink object,PostgresSqlBuilder builder) {builder.drop().space()._add("PUBLIC DATABASE LINK").space().name(object.getName());}
}
