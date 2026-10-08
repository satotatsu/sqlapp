module com.sqlapp.core.yugabyte {
	requires java.sql;
	requires com.sqlapp.core;
	requires com.sqlapp.core.postgres;
	exports com.sqlapp.data.db.dialect.yugabyte;
	exports com.sqlapp.data.db.dialect.yugabyte.resolver;
	provides com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver with
			com.sqlapp.data.db.dialect.yugabyte.resolver.YugabyteDialectResolver;
	provides com.sqlapp.jdbc.bulk.BulkInsertProvider with
			com.sqlapp.data.db.dialect.yugabyte.bulk.YugabyteBulkInsertProvider;
	provides com.sqlapp.jdbc.bulk.BulkUpsertProvider with
			com.sqlapp.data.db.dialect.yugabyte.bulk.YugabyteBulkUpsertProvider;
}
