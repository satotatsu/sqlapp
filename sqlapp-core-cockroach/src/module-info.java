module com.sqlapp.core.cockroach {
	requires java.sql;
	requires com.sqlapp.core;
	requires com.sqlapp.core.postgres;
	exports com.sqlapp.data.db.dialect.cockroach;
	exports com.sqlapp.data.db.dialect.cockroach.resolver;
	provides com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver with com.sqlapp.data.db.dialect.cockroach.resolver.CockroachDialectResolver;
	provides com.sqlapp.jdbc.bulk.BulkInsertProvider with com.sqlapp.data.db.dialect.cockroach.bulk.CockroachBulkInsertProvider;
	provides com.sqlapp.jdbc.bulk.BulkUpsertProvider with com.sqlapp.data.db.dialect.cockroach.bulk.CockroachBulkUpsertProvider;
}
