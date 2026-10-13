/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle.metadata;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.metadata.Postgres160CatalogReader;
import com.sqlapp.data.db.metadata.SchemaReader;
/** Keeps the PostgreSQL 17 catalog boundary with EPAS routine handling. */
public class EdbOracle17CatalogReader extends Postgres160CatalogReader {
	public EdbOracle17CatalogReader(Dialect dialect) { super(dialect); }
	@Override protected SchemaReader newSchemaReader() { return new EdbOracle17SchemaReader(getDialect()); }
	@Override protected com.sqlapp.data.db.metadata.PublicSynonymReader newPublicSynonymReader() { return new EdbOraclePublicSynonymReader(getDialect()); }
	@Override protected com.sqlapp.data.db.metadata.PublicDbLinkReader newPublicDbLinkReader() { return new EdbOraclePublicDbLinkReader(getDialect()); }

}
