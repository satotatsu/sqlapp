package com.sqlapp.data.db.dialect.postgres;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.db.dialect.postgres.PostgresSchemaObject.Kind;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;

class PostgresSchemaObjectsTest {
	@Test
	void refusesSilentAlterAndStaleSchemaNames() throws Exception {
		var original = new Schema("s");
		var target = original.clone();
		var object = new PostgresSchemaObject(Kind.COLLATION,"s","c",null,
				List.of("CREATE COLLATION s.c (locale='C')"),"DROP COLLATION s.c",Map.of());
		PostgresSchemaObjects.set(target,List.of(object));
		SqlFactory<Schema> factory=DialectHolder.postgreSQL180.createSqlFactoryRegistry().getSqlFactory(original,SqlType.ALTER);
		assertThrows(IllegalArgumentException.class,()->factory.createDiffSql(original.diff(target)));
		assertEquals(List.of("DROP COLLATION s.c"),PostgresSchemaObjects.dropSql(target));
		target.setName("renamed");
		assertThrows(IllegalArgumentException.class,()->PostgresSchemaObjects.createSql(target));
	}
	@Test
	void preservesDefinitionsIdentityAndMetadataThroughXmlAndClone() throws Exception {
		var schema = new Schema("Tenant");
		var object = new PostgresSchemaObject(Kind.COLLATION,"Tenant","Case",null,
				List.of("CREATE COLLATION \"Tenant\".\"Case\" (provider = libc, locale = 'C')"),
				"DROP COLLATION \"Tenant\".\"Case\"",Map.of("remarks","日本語 & < >"));
		PostgresSchemaObjects.set(schema,List.of(object));
		Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
		assertEquals(List.of(object),PostgresSchemaObjects.get(restored));
		assertEquals(List.of(object),PostgresSchemaObjects.get(schema.clone()));
		assertThrows(IllegalArgumentException.class,()->PostgresSchemaObjects.set(schema,List.of(object,object)));
		assertEquals(List.of(object),PostgresSchemaObjects.get(schema));
		SqlFactory<Schema> factory=DialectHolder.postgreSQL180.createSqlFactoryRegistry().getSqlFactory(restored,SqlType.CREATE);
		assertTrue(factory.createSql(restored).stream().anyMatch(sql->sql.getSqlText().equals(object.definition().get(0))));
	}

	@Test
	void preservesCaseAndParentIdentityAndRejectsCorruptPayload() {
		var schema=new Schema("s");
		var a=new PostgresSchemaObject(Kind.POLICY,"s","p","a",List.of("CREATE POLICY p ON s.a"),"DROP POLICY p ON s.a",Map.of());
		var b=new PostgresSchemaObject(Kind.POLICY,"s","p","b",List.of("CREATE POLICY p ON s.b"),"DROP POLICY p ON s.b",Map.of());
		PostgresSchemaObjects.set(schema,List.of(a,b));
		assertNotEquals(a.id(),b.id());
		schema.getSpecifics().put(PostgresSchemaObjects.SPECIFICS_KEY,"{\"format\":2,\"objects\":[]}");
		assertThrows(IllegalArgumentException.class,()->PostgresSchemaObjects.get(schema));
		schema.getSpecifics().put(PostgresSchemaObjects.SPECIFICS_KEY,"{\"format\":1,\"objects\":[],\"unsupported\":true}");
		assertThrows(RuntimeException.class,()->PostgresSchemaObjects.get(schema));
	}

	@Test
	void requiresExternalConnectionsAndEscapesSecretsWithoutMutatingXml() throws Exception {
		var schema=new com.sqlapp.data.schemas.Catalog("database");
		var object=new PostgresSchemaObject(Kind.SUBSCRIPTION,null,"Sub",null,
				List.of("CREATE SUBSCRIPTION \"Sub\" CONNECTION "+PostgresSchemaObjects.CONNECTION_TOKEN+" PUBLICATION p WITH (connect=false)"),
				"DROP SUBSCRIPTION \"Sub\"",Map.of("enabled","true"));
		PostgresSchemaObjects.set(schema,List.of(object));
		assertThrows(IllegalArgumentException.class,()->PostgresSchemaObjects.createSql(schema,Map.of()));
		String secret="host=localhost password='value\\quote'";
		String sql=PostgresSchemaObjects.createSql(schema,Map.of(object.id(),secret)).get(0);
		assertTrue(sql.contains("E'host=localhost password=''value\\\\quote'''"));
		assertFalse(schema.asXml().contains("password="));
		assertEquals("true",PostgresSchemaObjects.get(schema).get(0).attributes().get("enabled"));
	}
}
