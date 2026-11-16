/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.migration.MigrationModelReferences.Kind;
import com.sqlapp.data.schemas.migration.MigrationModelReferences.Reference;

class MigrationModelReferencesTest {

	@Test
	void findsDistinctReferencesInOrder() {
		final String sql = """
				SELECT c.id, o.total
				FROM /*$ref('stg_customer')*/stg_customer c
				JOIN (SELECT * FROM /*$source("raw_orders")*/main.raw_orders) o ON o.customer_id = c.id
				LEFT JOIN /*$ ref( 'stg_customer' ) */"Stg Customer" c2 ON c2.id = c.parent_id
				""";

		assertEquals(List.of(new Reference(Kind.REF, "stg_customer"), new Reference(Kind.SOURCE, "raw_orders")),
				MigrationModelReferences.find(sql));
	}

	@Test
	void replacesOnlyTheCommentAndItsPlaceholder() {
		final String sql = "SELECT * FROM (SELECT * FROM /*$source('raw')*/main.raw) r, /*$ref('顧客')*/顧客 k WHERE /*id*/1 = k.id";

		final String resolved = MigrationModelReferences.resolve(sql,
				reference -> reference.kind() == Kind.REF ? "\"m\".\"" + reference.name() + "\""
						: "read_csv('a$b.csv')");

		assertEquals(
				"SELECT * FROM (SELECT * FROM read_csv('a$b.csv')) r, \"m\".\"顧客\" k WHERE /*id*/1 = k.id",
				resolved);
	}

	@Test
	void rejectsReferenceWithoutPlaceholder() {
		final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> MigrationModelReferences.find("SELECT * FROM /*$ref('a')*/ WHERE 1=1"));

		assertTrue(e.getMessage().contains("placeholder"), e.getMessage());
		assertThrows(IllegalArgumentException.class,
				() -> MigrationModelReferences.find("SELECT * FROM /*$ref(a)*/a"));
	}

	@Test
	void sqlWithoutReferencesIsUnchanged() {
		final String sql = "SELECT /*$column*/id FROM t WHERE x = /*x*/1";

		assertTrue(MigrationModelReferences.find(sql).isEmpty());
		assertEquals(sql, MigrationModelReferences.resolve(sql, reference -> "unused"));
	}
}
