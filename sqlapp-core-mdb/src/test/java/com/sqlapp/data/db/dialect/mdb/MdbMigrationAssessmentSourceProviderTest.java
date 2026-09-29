/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

class MdbMigrationAssessmentSourceProviderTest {
	@Test
	void classifiesSavedQueriesWithoutCollectingSqlText() {
		final var queries = List.of(
				new MdbAssessmentSnapshot.SavedQuery("visible", "SELECT", false, false),
				new MdbAssessmentSnapshot.SavedQuery("union", "UNION", false, false),
				new MdbAssessmentSnapshot.SavedQuery("hidden", "SELECT", true, false),
				new MdbAssessmentSnapshot.SavedQuery("parameter", "SELECT", false, true),
				new MdbAssessmentSnapshot.SavedQuery("update", "UPDATE", false, false));
		final Map<String, Integer> counts = MdbMigrationAssessmentSourceProvider.savedQueryInventory(queries).stream()
				.collect(Collectors.toMap(item -> item.type(), item -> item.count()));
		assertEquals(2, counts.get("savedQueryViewCandidates"));
		assertEquals(3, counts.get("savedQueryManualPorts"));
		assertEquals(1, counts.get("savedQueryHidden"));
		assertEquals(1, counts.get("savedQueryParameterized"));
		assertEquals(1, counts.get("savedQueryActionOrSpecial"));
	}

	@Test
	void countsAccessIndexSemantics() {
		final var schema = new Schema("access");
		final var table = new Table("customer");
		final var ordinary = new Index("IX_NAME");
		final var unique = new Index("UX_CODE").setUnique(true);
		final var ignoreNulls = new Index("IX_OPTIONAL");
		ignoreNulls.getSpecifics().put(MdbFileLoader.INDEX_IGNORE_NULLS, "true");
		table.getIndexes().add(ordinary);
		table.getIndexes().add(unique);
		table.getIndexes().add(ignoreNulls);
		schema.getTables().add(table);
		final Map<String, Integer> counts = MdbMigrationAssessmentSourceProvider.indexInventory(schema).stream()
				.collect(Collectors.toMap(item -> item.type(), item -> item.count()));
		assertEquals(3, counts.get("accessIndexes"));
		assertEquals(1, counts.get("accessUniqueIndexes"));
		assertEquals(1, counts.get("accessIgnoreNullsIndexes"));
	}

	@Test
	void countsAccessColumnAndValidationSemantics() {
		final var schema = new Schema("access");
		final var table = new Table("orders");
		table.getColumns().add(new com.sqlapp.data.schemas.Column("ID").setIdentity(true));
		table.getColumns().add(new com.sqlapp.data.schemas.Column("Created").setDefaultValue("Now()"));
		table.getColumns().add(new com.sqlapp.data.schemas.Column("Total").setFormula("[Qty] * [Price]").setCheck(">= 0"));
		table.getConstraints().addCheckConstraint("CK_ORDERS", "[Qty] >= 0");
		schema.getTables().add(table);
		final Map<String, Integer> counts = MdbMigrationAssessmentSourceProvider.columnSemanticsInventory(schema).stream()
				.collect(Collectors.toMap(item -> item.type(), item -> item.count()));
		assertEquals(1, counts.get("accessAutoNumberColumns"));
		assertEquals(1, counts.get("accessColumnsWithDefaults"));
		assertEquals(1, counts.get("accessCalculatedColumns"));
		assertEquals(1, counts.get("accessColumnValidationRules"));
		assertEquals(1, counts.get("accessTableValidationRules"));
	}

	@Test
	void countsAccessRelationshipCascadeSemantics() {
		final var schema = new Schema("access");
		final var parent = new Table("parent");
		final var parentId = new Column("ID");
		parent.getColumns().add(parentId);
		final var child = new Table("child");
		final var childParentId = new Column("ParentID");
		child.getColumns().add(childParentId);
		schema.getTables().add(parent);
		schema.getTables().add(child);
		child.getConstraints().addForeignKeyConstraint("FK_CHILD_PARENT", childParentId, parentId)
				.setUpdateRule(CascadeRule.Cascade).setDeleteRule(CascadeRule.Cascade);
		final Map<String, Integer> counts = MdbMigrationAssessmentSourceProvider.relationshipInventory(schema).stream()
				.collect(Collectors.toMap(item -> item.type(), item -> item.count()));
		assertEquals(1, counts.get("accessRelationships"));
		assertEquals(1, counts.get("accessCascadeUpdateRelationships"));
		assertEquals(1, counts.get("accessCascadeDeleteRelationships"));
	}

	@Test
	void countsAccessTableAndColumnDescriptionsWithoutCopyingTheirText() {
		final var schema = new Schema("access");
		final var documented = new Table("documented").setRemarks("Business description");
		documented.getColumns().add(new Column("ID").setRemarks("Identifier"));
		documented.getColumns().add(new Column("Value"));
		schema.getTables().add(documented);
		schema.getTables().add(new Table("undocumented"));
		final var inventory = MdbMigrationAssessmentSourceProvider.descriptionInventory(schema);
		final Map<String, Integer> counts = inventory.stream()
				.collect(Collectors.toMap(item -> item.type(), item -> item.count()));
		assertEquals(1, counts.get("accessTablesWithDescriptions"));
		assertEquals(1, counts.get("accessColumnsWithDescriptions"));
		assertEquals(2, inventory.size());
	}
}
