/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 */
package com.sqlapp.data.schemas.viewpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

class SchemaViewpointResolverTest {

	@Test
	void resolvesViewpointFromSameSchemaModel() {
		Catalog catalog = catalog();
		SchemaViewpoints definitions = definitions();
		SchemaViewpointResolver resolver = new SchemaViewpointResolver();

		var resolution = resolver.resolve(catalog, definitions, "migration");

		assertEquals(List.of("COMPANY_MASTER", "EMPLOYEE_LIST"),
				resolution.tables().stream().map(Table::getName).toList());
	}

	@Test
	void rejectsAmbiguousTable() {
		Catalog catalog = catalog();
		catalog.getSchemas().add(new Schema("OTHER"));
		catalog.getSchemas().get("OTHER").getTables().add(new Table("EMPLOYEE_LIST"));
		SchemaViewpoints definitions = definitions();
		definitions.getViewpoints().getFirst().getTables().clear();
		definitions.getViewpoints().getFirst().getTables().add("EMPLOYEE_LIST");
		assertTrue(assertThrows(IllegalArgumentException.class,
				() -> new SchemaViewpointResolver().resolve(catalog, definitions, "migration"))
				.getMessage().contains("Ambiguous"));
	}

	@Test
	void rejectsEmptyViewpoint() {
		SchemaViewpoints definitions = definitions();
		definitions.getViewpoints().getFirst().getTables().clear();
		assertThrows(IllegalArgumentException.class,
				() -> new SchemaViewpointResolver().resolve(catalog(), definitions, "migration"));
	}

	@Test
	void rejectsYamlNullTablesWithConfigurationError() {
		SchemaViewpoints definitions = definitions();
		definitions.getViewpoints().getFirst().setTables(null);
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> new SchemaViewpointResolver().resolve(catalog(), definitions, "migration"));
		assertTrue(exception.getMessage().contains("contains no tables"));
	}

	private Catalog catalog() {
		Catalog catalog = new Catalog("CAT");
		Schema schema = new Schema("PUBLIC");
		schema.getTables().add(new Table("COMPANY_MASTER"));
		schema.getTables().add(new Table("EMPLOYEE_LIST"));
		catalog.getSchemas().add(schema);
		return catalog;
	}

	private SchemaViewpoints definitions() {
		SchemaViewpoints definitions = new SchemaViewpoints();
		SchemaViewpoint viewpoint = new SchemaViewpoint();
		viewpoint.setId("migration");
		viewpoint.getTables().add("CAT.PUBLIC.COMPANY_MASTER");
		viewpoint.getTables().add("PUBLIC.EMPLOYEE_LIST");
		definitions.getViewpoints().add(viewpoint);
		return definitions;
	}
}
