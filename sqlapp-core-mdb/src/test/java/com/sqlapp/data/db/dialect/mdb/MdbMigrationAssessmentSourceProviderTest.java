/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Index;
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
}
