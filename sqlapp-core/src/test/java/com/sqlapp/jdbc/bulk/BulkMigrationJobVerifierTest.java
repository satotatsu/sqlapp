/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class BulkMigrationJobVerifierTest {
	@Test
	void verificationResultOwnsValidImmutableChunkBoundaries() {
		final var chunks = new java.util.ArrayList<BulkMigrationVerificationChunk>();
		final var result = new BulkMigrationVerificationResult(10, 0, 0, chunks);
		chunks.add(new BulkMigrationVerificationChunk(0, 0, 0, "x", "x"));

		assertEquals(List.of(), result.getChunks());
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationVerificationResult(0, 0, 0, List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationVerificationResult(1, -1, 0, List.of()));
	}

	@Test
	void aggregatesResultsInDependencyOrder() {
		final Table expectedParent = table("PARENT", "parent");
		final Table expectedChild = table("CHILD", "child");
		expectedChild.getConstraints().addForeignKeyConstraint("FK_CHILD_PARENT",
				expectedChild.getColumns().get("ID"), expectedParent.getColumns().get("ID"));
		final Table actualParent = table("PARENT", "parent");
		final Table actualChild = table("CHILD", "changed");
		final var parent = BulkMigrationJobVerificationTask.builder().taskId("parent")
				.expected(expectedParent).actual(actualParent).chunkSize(1).build();
		final var child = BulkMigrationJobVerificationTask.builder().taskId("child")
				.expected(expectedChild).actual(actualChild).chunkSize(1).build();

		final var result = BulkMigrationJobVerifier.verify(List.of(child, parent));

		assertEquals(List.of("parent", "child"), result.getTasks().stream()
				.map(BulkMigrationJobTaskVerificationResult::getTaskId).toList());
		assertFalse(result.isMatch());
		assertEquals(1, result.getMismatchedTasks());
		assertEquals(2, result.getExpectedRows());
		assertEquals(2, result.getActualRows());
		assertEquals(List.of(1, 1), result.getTasks().stream()
				.map(task -> task.getVerificationResult().getChunkSize()).toList());
	}

	private static Table table(final String name, final String text) {
		final Table table = new Table(name);
		table.getColumns().add(new Column("ID"));
		table.getColumns().add(new Column("TXT"));
		table.getRows().add(row -> {
			row.put("ID", 1);
			row.put("TXT", text);
		});
		return table;
	}
}
