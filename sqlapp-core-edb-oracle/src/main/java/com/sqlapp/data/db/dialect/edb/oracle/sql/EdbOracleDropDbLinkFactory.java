/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle.sql;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.schemas.DbLink;
public class EdbOracleDropDbLinkFactory extends AbstractDropNamedObjectFactory<DbLink,PostgresSqlBuilder> {
	@Override protected void addDropObject(DbLink object,PostgresSqlBuilder builder) {builder.drop().space()._add("DATABASE LINK").space().name(object.getName());}
}
