/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
module com.sqlapp.core.aurora {
	requires java.sql;
	requires com.sqlapp.core;
	requires com.sqlapp.core.postgres;
	exports com.sqlapp.data.db.dialect.aurora;
	exports com.sqlapp.data.db.dialect.aurora.resolver;
	provides com.sqlapp.jdbc.bulk.BulkInsertProvider with com.sqlapp.data.db.dialect.aurora.bulk.AuroraBulkInsertProvider;
	provides com.sqlapp.jdbc.bulk.BulkUpsertProvider with com.sqlapp.data.db.dialect.aurora.bulk.AuroraBulkUpsertProvider;
	provides com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotProvider with com.sqlapp.data.db.dialect.aurora.bulk.AuroraSetBasedMigrationSnapshotProvider;
	provides com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver with com.sqlapp.data.db.dialect.aurora.resolver.AuroraDialectResolver;
}
