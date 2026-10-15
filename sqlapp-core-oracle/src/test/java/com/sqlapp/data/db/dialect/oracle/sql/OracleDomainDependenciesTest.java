/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.DomainColumn;
import com.sqlapp.data.schemas.DomainCheckConstraint;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;

class OracleDomainDependenciesTest {
	private Domain domain(String name, String dependencies) throws Exception {
		Domain domain = new Domain(name).setSchemaName("APP").setDefinition("CREATE DOMAIN APP." + name + " AS NUMBER");
		if (dependencies != null) domain.getSpecifics().put(OracleDomainDependencies.DEPENDENCIES, dependencies);
		return SchemaUtils.readXml(new java.io.StringReader(domain.asXml()));
	}

	@Test
	void createsDependenciesFirstAfterXml() throws Exception {
		var dependent = domain("A", "[[\"APP\",\"Z\"],[\"EXTERNAL\",\"D\"]]");
		var base = domain("Z", null);
		var differentCase = domain("z", null);
		assertEquals(List.of(differentCase, base, dependent), OracleDomainDependencies.sort(List.of(dependent, differentCase, base)));
		var factory = new Oracle23aiCreateDomainFactory();
		assertEquals(List.of(base, dependent), factory.sort(List.of(dependent, base)));
	}

	@Test
	void completeDefinitionDoesNotRequireDuplicateComponents() throws Exception {
		var domain = domain("SOURCE", null);
		domain.getColumns().add(new DomainColumn("OPTIONAL"));
		domain.getChecks().add(new DomainCheckConstraint("OPTIONAL_CHECK", null));
		assertEquals("CREATE DOMAIN APP.SOURCE AS NUMBER", DialectHolder.oracle26aiDialect.createSqlFactoryRegistry()
				.createSql(domain, SqlType.CREATE).get(0).getSqlText());
	}

	@Test
	void rejectsCyclesAndMalformedReferences() throws Exception {
		var a = domain("A", "[[\"APP\",\"B\"]]");
		var b = domain("B", "[[\"APP\",\"A\"]]");
		assertTrue(assertThrows(IllegalArgumentException.class,
				() -> OracleDomainDependencies.sort(List.of(a, b))).getMessage().contains("Cyclic"));
		var invalid = domain("BROKEN", "[[\"APP\"]]");
		assertThrows(IllegalArgumentException.class, () -> OracleDomainDependencies.sort(List.of(invalid)));
	}
}
