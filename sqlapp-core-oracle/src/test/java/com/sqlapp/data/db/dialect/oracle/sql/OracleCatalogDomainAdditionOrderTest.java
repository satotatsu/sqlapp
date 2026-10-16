/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class OracleCatalogDomainAdditionOrderTest {
	private Catalog original() {
		Catalog catalog = new Catalog("CAT");
		catalog.getSchemas().add(new Schema("APP"));
		catalog.getSchemas().add(new Schema("BASE"));
		return catalog;
	}

	private Catalog target(Catalog original, boolean flexible) {
		Catalog catalog = original.clone();
		Schema app = catalog.getSchemas().get("APP"), base = catalog.getSchemas().get("BASE");
		base.getSequences().add(new Sequence("SEQ"));
		base.getTypes().add(new Type("X").setStatement("OBJECT (N NUMBER)"));
		Domain scalar = new Domain("D").setDataType(DataType.INT).setDefaultValue("BASE.SEQ.NEXTVAL");
		base.getDomains().add(scalar);
		Domain reference = scalar;
		if (flexible) {
			Domain choice = new Domain("CHOICE").setDefinition(
					"CREATE FLEXIBLE DOMAIN APP.CHOICE (V) CHOOSE DOMAIN USING (K NUMBER) FROM DECODE(K,1,BASE.D(V))");
			choice.getSpecifics().put(OracleDomainAssociationUtils.KIND, "FLEXIBLE");
			choice.getSpecifics().put(OracleDomainDependencies.DEPENDENCIES, "[[\"BASE\",\"D\"]]");
			choice.getColumns().add(new DomainColumn("V"));
			DomainColumn discriminator = new DomainColumn("K");
			discriminator.getSpecifics().put(OracleDomainAssociationUtils.DISCRIMINANT, true);
			choice.getColumns().add(discriminator);
			app.getDomains().add(choice);
			reference = choice;
		}
		Table table = new Table("T");
		app.getTables().add(table);
		table.getColumns().add(new Column("PAYLOAD").setDataType(DataType.INT).setDomain(reference)
				.setDomainColumnName(flexible ? "V" : null));
		if (flexible)
			table.getColumns().add(
					new Column("CATEGORY").setDataType(DataType.INT).setDomain(reference).setDomainColumnName("K"));
		table.getIndexes().add("IX", table.getColumns().get("PAYLOAD"));
		app.getViews().add(new View("TV").setStatement("SELECT PAYLOAD FROM APP.T"));
		return catalog;
	}

	@Test
	void scalarDomainsFollowPrerequisitesAndPrecedeCrossSchemaTables() throws Exception {
		for (var dialect : List.of(DialectHolder.oracle23aiDialect, DialectHolder.oracle26aiDialect)) {
			Catalog original = original();
			Catalog target = SchemaUtils.readXml(new StringReader(target(original, false).asXml()));
			SqlFactory<Catalog> factory = dialect.createSqlFactoryRegistry().getSqlFactory(target, State.Modified);
			var operations = factory.createDiffSql(original.diff(target));
			var objects = operations.stream().map(o -> o.getOriginal()).toList();
			var base = target.getSchemas().get("BASE");
			var app = target.getSchemas().get("APP");
			assertTrue(objects.indexOf(base.getSequences().get("SEQ")) < objects.indexOf(base.getDomains().get("D")),
					operations.toString());
			assertTrue(objects.indexOf(base.getTypes().get("X")) < objects.indexOf(base.getDomains().get("D")),
					operations.toString());
			assertTrue(objects.indexOf(base.getDomains().get("D")) < objects.indexOf(app.getTables().get("T")),
					operations.toString());
			assertTrue(objects.indexOf(app.getTables().get("T")) < objects
					.indexOf(app.getTables().get("T").getIndexes().get("IX")), operations.toString());
			assertTrue(objects.indexOf(app.getTables().get("T").getIndexes().get("IX")) < objects
					.indexOf(app.getViews().get("TV")), operations.toString());
			assertEquals(List.of("APP", "BASE"), target.getSchemas().stream().map(Schema::getName).toList());
			factory.getOptions().setDecorateSchemaName(false);
			assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(original.diff(target)));
		}
	}

	@Test
	void flexibleDomainDependenciesPrecedeConsumersAcrossSchemas() throws Exception {
		Catalog original = original();
		Catalog target = SchemaUtils.readXml(new StringReader(target(original, true).asXml()));
		SqlFactory<Catalog> factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(target,
				State.Modified);
		var operations = factory.createDiffSql(original.diff(target));
		var objects = operations.stream().map(o -> o.getOriginal()).toList();
		var base = target.getSchemas().get("BASE").getDomains().get("D");
		var choice = target.getSchemas().get("APP").getDomains().get("CHOICE");
		var table = target.getSchemas().get("APP").getTables().get("T");
		assertTrue(objects.indexOf(base) < objects.indexOf(choice), operations.toString());
		assertTrue(objects.indexOf(choice) < objects.indexOf(table), operations.toString());
		assertEquals(1, objects.stream().filter(o -> o == choice).count());
		assertTrue(operations.stream().filter(o -> o.getOriginal() == table)
				.anyMatch(o -> o.getSqlText().contains("USING")));
	}

	@Test
	void existingTablesAndNewSchemasUseTheSameAdditionOrdering() {
		Catalog original = original();
		Table existing = new Table("EXISTING");
		original.getSchemas().get("APP").getTables().add(existing);
		existing.getColumns().add(new Column("V").setDataType(DataType.INT));
		Catalog target = original.clone();
		Domain domain = new Domain("D").setDataType(DataType.INT);
		target.getSchemas().get("BASE").getDomains().add(domain);
		target.getSchemas().get("APP").getTables().get("EXISTING").getColumns().get("V").setDomain(domain);
		SqlFactory<Catalog> factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(target,
				State.Modified);
		var operations = factory.createDiffSql(original.diff(target));
		assertTrue(operations.get(0).getOriginal() instanceof Domain, operations.toString());
		assertTrue(operations.get(1).getTarget() instanceof Column, operations.toString());
		Catalog empty = new Catalog("CAT");
		Catalog addedSchemas = target(original(), false);
		operations = factory.createDiffSql(empty.diff(addedSchemas));
		var objects = operations.stream().map(o -> o.getOriginal()).toList();
		assertTrue(objects.indexOf(addedSchemas.getSchemas().get("BASE").getDomains().get("D")) < objects
				.indexOf(addedSchemas.getSchemas().get("APP").getTables().get("T")), operations.toString());
	}

	@Test
	void rootPrerequisitesPrecedeSchemaObjectsAndDomains() {
		Domain domain = new Domain("D").setSchemaName("BASE").setDataType(DataType.INT);
		Table table = new Table("T").setSchemaName("APP");
		table.getColumns().add(new Column("V").setDataType(DataType.INT).setDomain(domain));
		var sequence = new Sequence("SEQ").setSchemaName("BASE");
		var user = new User("BASE");
		var operations = List.of(new SqlOperation("domain", SqlType.CREATE, domain),
				new SqlOperation("table", SqlType.CREATE, table),
				new SqlOperation("sequence", SqlType.CREATE, sequence), new SqlOperation("user", SqlType.CREATE, user));
		assertEquals(List.of("user", "sequence", "domain", "table"),
				OracleDomainMigrationOrder.additions(operations).stream().map(SqlOperation::getSqlText).toList());
	}

	@Test
	void catalogRejectsDanglingTableAndFlexibleDomainReferencesOnRemoval() {
		for (boolean flexible : new boolean[] { false, true }) {
			Catalog original = target(original(), flexible);
			Catalog target = original.clone();
			target.getSchemas().get("BASE").getDomains().clear();
			SqlFactory<Catalog> factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry()
					.getSqlFactory(target, State.Modified);
			var error = assertThrows(IllegalArgumentException.class,
					() -> factory.createDiffSql(original.diff(target)));
			assertTrue(error.getMessage().contains("BASE") && error.getMessage().contains("D"), error.getMessage());
			assertTrue(error.getMessage().contains(flexible ? "CHOICE" : "PAYLOAD"), error.getMessage());
		}
	}

	@Test
	void rejectsCrossSchemaCyclesAndLeavesUnrelatedOperationsInOrder() {
		Catalog original = original();
		Catalog target = original.clone();
		for (String schema : List.of("APP", "BASE")) {
			Domain domain = new Domain("D").setDataType(DataType.INT);
			domain.getSpecifics().put(OracleDomainDependencies.DEPENDENCIES,
					"[[\"" + (schema.equals("APP") ? "BASE" : "APP") + "\",\"D\"]]");
			target.getSchemas().get(schema).getDomains().add(domain);
		}
		SqlFactory<Catalog> factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(target,
				State.Modified);
		assertTrue(assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(original.diff(target)))
				.getMessage().contains("Cyclic"));
		var operations = List.of(new SqlOperation("first", SqlType.ALTER), new SqlOperation("second", SqlType.ALTER));
		assertSame(operations, OracleDomainMigrationOrder.additions(operations));
	}
}
