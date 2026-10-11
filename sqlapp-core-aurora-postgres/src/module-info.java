/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
module com.sqlapp.core.aurora.postgres {
	requires java.sql;
	requires com.sqlapp.core;
	requires com.sqlapp.core.postgres;
	exports com.sqlapp.data.db.dialect.aurora.postgres;
	exports com.sqlapp.data.db.dialect.aurora.postgres.resolver;
	provides com.sqlapp.jdbc.bulk.BulkInsertProvider with com.sqlapp.data.db.dialect.aurora.postgres.bulk.AuroraBulkInsertProvider;
	provides com.sqlapp.jdbc.bulk.BulkUpsertProvider with com.sqlapp.data.db.dialect.aurora.postgres.bulk.AuroraBulkUpsertProvider;
	provides com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotProvider with com.sqlapp.data.db.dialect.aurora.postgres.bulk.AuroraSetBasedMigrationSnapshotProvider;
	provides com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver with com.sqlapp.data.db.dialect.aurora.postgres.resolver.AuroraDialectResolver;
}
