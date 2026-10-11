/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
module com.sqlapp.core.alloydb {
	requires java.sql;
	requires com.sqlapp.core;
	requires com.sqlapp.core.postgres;
	exports com.sqlapp.data.db.dialect.alloydb;
	exports com.sqlapp.data.db.dialect.alloydb.sql;
	exports com.sqlapp.data.db.dialect.alloydb.metadata;
	exports com.sqlapp.data.db.dialect.alloydb.resolver;
	provides com.sqlapp.jdbc.bulk.BulkInsertProvider with
			com.sqlapp.data.db.dialect.alloydb.bulk.AlloyDBBulkInsertProvider;
	provides com.sqlapp.jdbc.bulk.BulkUpsertProvider with
			com.sqlapp.data.db.dialect.alloydb.bulk.AlloyDBBulkUpsertProvider;
	provides com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotProvider with
			com.sqlapp.data.db.dialect.alloydb.bulk.AlloyDBSetBasedMigrationSnapshotProvider;
	provides com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver with
			com.sqlapp.data.db.dialect.alloydb.resolver.AlloyDBDialectResolver;
}
