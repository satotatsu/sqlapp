/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle.metadata;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.metadata.Postgres150SchemaReader;
import com.sqlapp.data.db.metadata.FunctionReader;
/** Uses the nearest compatible schema reader and preserves SPL definitions. */
public class EdbOracle15SchemaReader extends Postgres150SchemaReader {
	protected EdbOracle15SchemaReader(Dialect dialect) { super(dialect); }
	@Override protected FunctionReader newFunctionReader() { return new EdbOracleFunctionReader(getDialect()); }
	@Override protected com.sqlapp.data.db.metadata.PackageReader newPackageReader() { return new EdbOraclePackageReader(getDialect()); }
	@Override protected com.sqlapp.data.db.metadata.PackageBodyReader newPackageBodyReader() { return new EdbOraclePackageBodyReader(getDialect()); }

	@Override protected com.sqlapp.data.db.metadata.SynonymReader newSynonymReader() { return new EdbOracleSynonymReader(getDialect()); }
	@Override protected com.sqlapp.data.db.metadata.DbLinkReader newDbLinkReader() { return new EdbOracleDbLinkReader(getDialect()); }

}
