/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.dialect.hsql.HsqlDdlDependencies.Dependency;
import com.sqlapp.data.db.dialect.hsql.HsqlDdlDependencies.ObjectId;

class HsqlDdlDependenciesTest {
	@Test
	void rejectsStaleUnknownAndCyclicDependenciesBeforeReturningSql() {
		var schema = new Schema("APP");
		var a = new Table("A").setSchemaName("APP");
		var b = new Table("B").setSchemaName("APP");
		var idA = new ObjectId("RELATION", "APP", "A");
		var idB = new ObjectId("RELATION", "APP", "B");
		HsqlDdlDependencies.set(schema, List.of(new Dependency(idA, idB), new Dependency(idB, idA)));
		var operations = List.of(new SqlOperation("CREATE A", SqlType.CREATE, a),
				new SqlOperation("CREATE B", SqlType.CREATE, b));
		assertThrows(IllegalArgumentException.class, () -> HsqlDdlDependencies.order(operations, List.of(schema)));
		assertThrows(IllegalArgumentException.class, () -> HsqlDdlDependencies.set(schema,
				List.of(new Dependency(idA, new ObjectId("UNKNOWN", "APP", "B")))));
		var renamed = schema.clone().setName("RENAMED");
		assertThrows(IllegalArgumentException.class, () -> HsqlDdlDependencies.get(renamed));
		schema.getSpecifics().put(HsqlDdlDependencies.SPECIFICS_KEY,
				"{\"format\":2,\"schema\":\"APP\",\"dependencies\":[]}");
		assertThrows(IllegalArgumentException.class, () -> HsqlDdlDependencies.get(schema));
	}

	@Test
	void preservesOrderForUnrelatedObjectsAndAcceptsExternalPrerequisites() {
		var schema = new Schema("APP");
		HsqlDdlDependencies.set(schema, List
				.of(new Dependency(new ObjectId("RELATION", "APP", "A"), new ObjectId("RELATION", "EXTERNAL", "T"))));
		var a = new Table("A").setSchemaName("APP");
		var b = new Table("B").setSchemaName("APP");
		var operations = List.of(new SqlOperation("A", SqlType.CREATE, a), new SqlOperation("B", SqlType.CREATE, b));
		assertEquals(operations, HsqlDdlDependencies.order(operations, List.of(schema)));
		var grant = new SqlOperation("GRANT SELECT", SqlType.GRANT, new com.sqlapp.data.schemas.Catalog("PUBLIC"));
		assertEquals(List.of(operations.get(0), grant),
				HsqlDdlDependencies.order(List.of(grant, operations.get(0)), List.of(schema)));
	}
}
