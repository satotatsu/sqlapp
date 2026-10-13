/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle.metadata;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.metadata.Postgres140CatalogReader;
import com.sqlapp.data.db.metadata.SchemaReader;
/** Keeps the PostgreSQL 14 catalog boundary with EPAS routine handling. */
public class EdbOracle14CatalogReader extends Postgres140CatalogReader {
	public EdbOracle14CatalogReader(Dialect dialect) { super(dialect); }
	@Override protected SchemaReader newSchemaReader() { return new EdbOracle14SchemaReader(getDialect()); }
	@Override protected com.sqlapp.data.db.metadata.PublicSynonymReader newPublicSynonymReader() { return new EdbOraclePublicSynonymReader(getDialect()); }
	@Override protected com.sqlapp.data.db.metadata.PublicDbLinkReader newPublicDbLinkReader() { return new EdbOraclePublicDbLinkReader(getDialect()); }

}
