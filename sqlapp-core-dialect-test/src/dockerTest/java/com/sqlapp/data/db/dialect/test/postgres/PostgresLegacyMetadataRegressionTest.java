/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.postgres;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.sqlapp.data.db.dialect.DialectResolver;

class PostgresLegacyMetadataRegressionTest {
	@ParameterizedTest
	@ValueSource(strings = { "postgres:9.6.24", "postgres:10.23-bullseye" })
	void preservesSequenceAndAdvancedIndexMetadataAcross10Boundary(String image) throws Exception {
		try (var db = new PostgreSQLContainer(image)) {
			db.start();
			try (var c = db.createConnection("")) {
				PostgresMetadataRegressionAssertions.verify(c, DialectResolver.getInstance().getDialect(c));
				var objects = new com.sqlapp.data.db.dialect.postgres.metadata.PostgresSchemaObjectReader(
						DialectResolver.getInstance().getDialect(c));
				objects.readSchema(c, "public");
				objects.readCatalog(c);
				verifyObjectAlter(c,DialectResolver.getInstance().getDialect(c),objects);
			}
		}
	}
	private void verifyObjectAlter(java.sql.Connection connection,com.sqlapp.data.db.dialect.Dialect dialect,
			com.sqlapp.data.db.dialect.postgres.metadata.PostgresSchemaObjectReader reader) throws Exception {
		try(var statement=connection.createStatement()) {
			statement.execute("CREATE SCHEMA alter_objects");
			statement.execute("CREATE FOREIGN DATA WRAPPER alter_wrapper NO HANDLER NO VALIDATOR");
			statement.execute("CREATE SERVER alter_server FOREIGN DATA WRAPPER alter_wrapper OPTIONS (host 'old.invalid')");
			statement.execute("CREATE FOREIGN TABLE alter_objects.remote (id integer DEFAULT 1) SERVER alter_server");
			statement.execute("CREATE TABLE alter_objects.items (id integer, amount integer)");
			statement.execute("CREATE POLICY visible ON alter_objects.items USING (id>0)");
			statement.execute("ALTER TABLE alter_objects.items ENABLE ROW LEVEL SECURITY");
		}
		var original=snapshot(connection,reader);
		try(var statement=connection.createStatement()) {
			statement.execute("ALTER SERVER alter_server OPTIONS (SET host 'new.invalid', ADD port '1234')");
			statement.execute("ALTER FOREIGN TABLE alter_objects.remote ALTER COLUMN id SET DEFAULT 2");
			statement.execute("ALTER POLICY visible ON alter_objects.items USING (id>2)");
			statement.execute("ALTER TABLE alter_objects.items FORCE ROW LEVEL SECURITY");
		}
		var target=snapshot(connection,reader);
		var factory=dialect.createSqlFactoryRegistry().getSqlFactory(original,com.sqlapp.data.db.sql.SqlType.ALTER);
		for(var pair:java.util.List.of(java.util.List.of(target,original),java.util.List.of(original,target))) {
			try(var statement=connection.createStatement()) {
				for(var operation:factory.createDiffSql(pair.get(0).diff(pair.get(1)))) statement.execute(operation.getSqlText());
			}
			var actual=snapshot(connection,reader);
			org.junit.jupiter.api.Assertions.assertEquals(com.sqlapp.data.db.dialect.postgres.PostgresSchemaObjects.get(pair.get(1)),
					com.sqlapp.data.db.dialect.postgres.PostgresSchemaObjects.get(actual));
			org.junit.jupiter.api.Assertions.assertEquals(com.sqlapp.data.db.dialect.postgres.PostgresSchemaObjects.get(pair.get(1).getSchemas().getFirst()),
					com.sqlapp.data.db.dialect.postgres.PostgresSchemaObjects.get(actual.getSchemas().getFirst()));
		}
	}
	private com.sqlapp.data.schemas.Catalog snapshot(java.sql.Connection connection,
			com.sqlapp.data.db.dialect.postgres.metadata.PostgresSchemaObjectReader reader) throws Exception {
		var catalog=new com.sqlapp.data.schemas.Catalog(connection.getCatalog());
		var schema=new com.sqlapp.data.schemas.Schema("alter_objects");
		com.sqlapp.data.db.dialect.postgres.PostgresSchemaObjects.set(catalog,reader.readCatalog(connection));
		com.sqlapp.data.db.dialect.postgres.PostgresSchemaObjects.set(schema,reader.readSchema(connection,schema.getName()));
		catalog.getSchemas().add(schema);
		return catalog;
	}
}
