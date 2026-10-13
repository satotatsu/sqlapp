/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle;

import com.sqlapp.data.db.dialect.postgres.Postgres160;

/** EDB Postgres Advanced Server 16; PostgreSQL-compatible baseline. */
public class EdbOracle16 extends Postgres160 implements EdbOracle {
	private static final long serialVersionUID = 1L;
	public EdbOracle16() { super(() -> null); }
	@Override public String getProductName() { return "EDB Postgres Advanced Server"; }
	@Override public String getSimpleName() { return "edb-oracle"; }
	@Override protected void registerDataType() {
		super.registerDataType();
		EdbOracleTypes.register(this);
	}
	@Override public com.sqlapp.data.db.metadata.CatalogReader getCatalogReader() {
		return new com.sqlapp.data.db.dialect.edb.oracle.metadata.EdbOracle16CatalogReader(this);
	}

	@Override public com.sqlapp.data.db.sql.SqlFactoryRegistry createSqlFactoryRegistry() {
		var registry=super.createSqlFactoryRegistry();
		registry.registerSqlFactory(com.sqlapp.data.schemas.Package.class,com.sqlapp.data.db.sql.SqlType.CREATE,com.sqlapp.data.db.dialect.edb.oracle.sql.EdbOracleCreatePackageFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.PackageBody.class,com.sqlapp.data.db.sql.SqlType.CREATE,com.sqlapp.data.db.dialect.edb.oracle.sql.EdbOracleCreatePackageBodyFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Package.class,com.sqlapp.data.db.sql.SqlType.DROP,com.sqlapp.data.db.dialect.edb.oracle.sql.EdbOracleDropPackageFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.PackageBody.class,com.sqlapp.data.db.sql.SqlType.DROP,com.sqlapp.data.db.dialect.edb.oracle.sql.EdbOracleDropPackageBodyFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Synonym.class,com.sqlapp.data.db.sql.SqlType.CREATE,com.sqlapp.data.db.dialect.edb.oracle.sql.EdbOracleCreateSynonymFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Synonym.class,com.sqlapp.data.db.sql.SqlType.DROP,com.sqlapp.data.db.dialect.edb.oracle.sql.EdbOracleDropSynonymFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.PublicSynonym.class,com.sqlapp.data.db.sql.SqlType.CREATE,com.sqlapp.data.db.dialect.edb.oracle.sql.EdbOracleCreatePublicSynonymFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.PublicSynonym.class,com.sqlapp.data.db.sql.SqlType.DROP,com.sqlapp.data.db.dialect.edb.oracle.sql.EdbOracleDropPublicSynonymFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.DbLink.class,com.sqlapp.data.db.sql.SqlType.CREATE,com.sqlapp.data.db.dialect.edb.oracle.sql.EdbOracleCreateDbLinkFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.DbLink.class,com.sqlapp.data.db.sql.SqlType.DROP,com.sqlapp.data.db.dialect.edb.oracle.sql.EdbOracleDropDbLinkFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.PublicDbLink.class,com.sqlapp.data.db.sql.SqlType.CREATE,com.sqlapp.data.db.dialect.edb.oracle.sql.EdbOracleCreatePublicDbLinkFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.PublicDbLink.class,com.sqlapp.data.db.sql.SqlType.DROP,com.sqlapp.data.db.dialect.edb.oracle.sql.EdbOracleDropPublicDbLinkFactory.class);
		return registry;
	}

}
