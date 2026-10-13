/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle.sql;
import com.sqlapp.data.db.sql.AbstractCreateNamedObjectFactory;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.schemas.DbLink;
/** Requires explicit transport and supplied credentials for remote link recreation. */
public class EdbOracleCreateDbLinkFactory extends AbstractCreateNamedObjectFactory<DbLink,PostgresSqlBuilder> {
	@Override protected void addCreateObject(DbLink object,PostgresSqlBuilder builder) {
		Object configured=object.getSpecifics().get("edb.dbLink.transport");
		if(!(configured instanceof String transport) || !java.util.Set.of("postgres_fdw","oci","legacy").contains(transport)) throw new IllegalArgumentException("Set edb.dbLink.transport to postgres_fdw, oci or legacy before recreating an EPAS database link");
		if(object.getDataSource()==null || object.getDataSource().isBlank()) throw new IllegalArgumentException("EPAS database link requires a connection string");
		if(object.getUserId()==null || object.getUserId().isBlank() || object.getPassword()==null || object.isPasswordEncrypted()) throw new IllegalArgumentException("EPAS database link requires an explicit userId and plaintext password supplied by the caller; metadata does not export passwords");
		builder.create().space()._add("DATABASE LINK").space().name(object.getName());
		builder.space()._add("CONNECT TO").space().name(object.getUserId()).space()._add("IDENTIFIED BY").space();
		EdbOracleSqlLiterals.add(builder,object.getPassword());
		builder.space()._add("USING").space();
		if(!transport.equals("legacy")) builder._add(transport).space();
		EdbOracleSqlLiterals.add(builder,object.getDataSource());
	}
}
