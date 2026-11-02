/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.*;

class TransferNameMappingScopeServiceTest {
	@TempDir
	Path directory;
	private final TransferNameMappingScopeService service = new TransferNameMappingScopeService();
	private final TransferNameMappingIO io = new TransferNameMappingIO();

	private BulkMigrationJobDraftBuilder.SchemaSnapshot schema() throws Exception {
		var schema = new Schema("PUBLIC");
		for (String name : List.of("KEEP", "OLD", "NEW")) {
			var table = new Table(name);
			table.getColumns().add(new Column("ID").setDataType(DataType.INT));
			table.getColumns().add(new Column("NOTE").setDataType(DataType.VARCHAR));
			schema.getTables().add(table);
		}
		var file = directory.resolve("schema.xml");
		schema.writeXml(file.toFile());
		return new BulkMigrationJobDraftBuilder().inspect(file);
	}

	private TransferNameMappingIO.Configuration mapping() {
		return io.validate(
				"formatVersion: 1\ntables:\n  PUBLIC.KEEP: {targetTable: CURRENT, columns: {ID: KEY_ID}}\n  OLD: {targetTable: LEGACY, columns: {ID: OLD_ID, NOTE: OLD_NOTE}}\n");
	}

	@Test
	void previewsWholeExcludedGroupsPreservingRetainedOverridesAndSourceBytes() throws Exception {
		var snapshot = schema();
		var mapping = mapping();
		byte[] original = Files.readAllBytes(snapshot.file());
		var review = service.review(snapshot, List.of("KEEP", "NEW"), mapping);
		assertEquals(snapshot.fingerprint(), review.schemaFingerprint());
		assertTrue(review.mappingFingerprint().matches("sha256:[0-9a-f]{64}"));
		assertEquals(List.of(new TransferNameMappingScopeService.Excluded("OLD", "PUBLIC.OLD", "LEGACY", 2)),
				review.excluded());
		assertEquals(Map.of("PUBLIC.KEEP", mapping.tables().get("PUBLIC.KEEP")), review.retained().tables());
		assertEquals(2, mapping.tables().size());
		assertArrayEquals(original, Files.readAllBytes(snapshot.file()));
		assertThrows(UnsupportedOperationException.class, () -> review.excluded().clear());
		try (var files = Files.list(directory)) {
			assertEquals(1, files.count());
		}
		assertEquals(review, service.review(snapshot.file(), List.of("KEEP", "NEW"), mapping));
	}

	@Test
	void unknownOrDuplicatedSourceAliasesCannotBeSilentlyDiscarded() throws Exception {
		var snapshot = schema();
		for (String rules : List.of("  UNKNOWN: {}\n", "  KEEP: {}\n  PUBLIC.KEEP: {}\n"))
			assertThrows(IllegalArgumentException.class,
					() -> service.review(snapshot, List.of("NEW"), io.validate("formatVersion: 1\ntables:\n" + rules)));
		assertThrows(IllegalArgumentException.class, () -> service.review(snapshot, List.of("UNKNOWN"), mapping()));
		assertThrows(IllegalArgumentException.class, () -> service.review(snapshot, List.of(), mapping()));
		assertThrows(IllegalArgumentException.class,
				() -> service.review(snapshot, List.of("KEEP", "PUBLIC.KEEP"), mapping()));
	}

	@Test
	void scopeReviewDoesNotPretendToValidateRetainedColumnRulesAndCanProduceEmptyMappings() throws Exception {
		var snapshot = schema();
		var mapping = io.validate("formatVersion: 1\ntables:\n  KEEP: {columns: {MISSING: X}}\n");
		var unchanged = service.review(snapshot, List.of("KEEP"), mapping);
		assertTrue(unchanged.excluded().isEmpty());
		assertEquals(mapping, unchanged.retained());
		assertTrue(service.review(snapshot, List.of("NEW"), mapping).retained().tables().isEmpty());
		assertThrows(IllegalArgumentException.class,
				() -> new TransferNameMappingInspectionService().inspect(snapshot, List.of("KEEP"), mapping));
	}

	@Test
	void changedCapturedSchemaIsRejected() throws Exception {
		var snapshot = schema();
		Files.writeString(snapshot.file(), Files.readString(snapshot.file()) + "\n<!-- changed -->");
		assertThrows(IllegalArgumentException.class, () -> service.review(snapshot, List.of("KEEP"), mapping()));
	}
}
