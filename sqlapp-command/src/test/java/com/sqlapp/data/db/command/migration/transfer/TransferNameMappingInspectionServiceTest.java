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

class TransferNameMappingInspectionServiceTest {
	@TempDir
	Path directory;
	private final TransferNameMappingInspectionService service = new TransferNameMappingInspectionService();
	private final TransferNameMappingIO io = new TransferNameMappingIO();

	private BulkMigrationJobDraftBuilder.SchemaSnapshot schema() throws Exception {
		var schema = new Schema("PUBLIC");
		var parent = new Table("顧客");
		var child = new Table("注文");
		parent.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		parent.setPrimaryKey("PK_CUSTOMERS", parent.getColumns().get("ID"));
		child.getColumns().add(new Column("親ID").setDataType(DataType.INT));
		child.getColumns().add(new Column("数量").setDataType(DataType.INT));
		child.getConstraints().addForeignKeyConstraint("FK_CUSTOMER", child.getColumns().get("親ID"),
				parent.getColumns().get("ID"));
		schema.getTables().add(parent);
		schema.getTables().add(child);
		Path file = directory.resolve("schema.xml");
		schema.writeXml(file.toFile());
		return new BulkMigrationJobDraftBuilder().inspect(file);
	}

	private TransferNameMappingIO.Configuration mapping(String rules) {
		return io.validate("formatVersion: 1\ntables:\n" + rules);
	}

	@Test
	void resolvesMappedKeylessRelationshipsAndReturnsPinnedImmutableMetadataWithoutWriting() throws Exception {
		var snapshot = schema();
		byte[] original = Files.readAllBytes(snapshot.file());
		var mapping = mapping(
				"  PUBLIC.顧客: {targetTable: PEOPLE, columns: {ID: OWNER_ID}}\n  注文: {targetTable: LOGS, columns: {親ID: OWNER_ID}}\n");
		var result = service.inspect(snapshot, List.of("注文", "顧客"), mapping);
		assertEquals(snapshot.file(), result.schemaFile());
		assertEquals(snapshot.fingerprint(), result.schemaFingerprint());
		assertTrue(result.mappingFingerprint().matches("sha256:[0-9a-f]{64}"));
		assertEquals(List.of("PEOPLE", "LOGS"), result.tables().stream().map(table -> table.target()).toList());
		var key = result.tables().getFirst().columns().getFirst();
		assertEquals("ID", key.source());
		assertEquals("OWNER_ID", key.target());
		assertTrue(key.primaryKey());
		assertFalse(key.nullable());
		assertEquals("INT", key.sourceType());
		var child = result.tables().getLast().columns().getFirst();
		assertFalse(child.primaryKey());
		assertTrue(child.nullable());
		assertThrows(UnsupportedOperationException.class, () -> result.tables().clear());
		assertThrows(UnsupportedOperationException.class, () -> result.tables().getFirst().columns().clear());
		assertArrayEquals(original, Files.readAllBytes(snapshot.file()));
		try (var files = Files.list(directory)) {
			assertEquals(1, files.count());
		}
		assertEquals(result, service.inspect(snapshot.file(), List.of("注文", "顧客"), mapping));
	}

	@Test
	void rejectsUnknownColumnsOutsideScopeMappingsAndMissingReferencedParents() throws Exception {
		var snapshot = schema();
		assertThrows(IllegalArgumentException.class,
				() -> service.inspect(snapshot, List.of("顧客"), mapping("  顧客: {columns: {MISSING: X}}\n")));
		assertThrows(IllegalArgumentException.class,
				() -> service.inspect(snapshot, List.of("顧客"), mapping("  注文: {targetTable: X}\n")));
		assertThrows(IllegalArgumentException.class,
				() -> service.inspect(snapshot, List.of("注文"), io.validate("formatVersion: 1\ntables: {}")));
		assertThrows(IllegalArgumentException.class,
				() -> service.inspect(snapshot, List.of("UNKNOWN"), io.validate("formatVersion: 1\ntables: {}")));
	}

	@Test
	void usesExecutionCollisionAndAliasRulesButAllowsAtomicColumnSwaps() throws Exception {
		var snapshot = schema();
		var selected = List.of("顧客", "注文");
		assertThrows(IllegalArgumentException.class, () -> service.inspect(snapshot, selected,
				mapping("  顧客: {targetTable: SAME}\n  注文: {targetTable: same}\n")));
		assertThrows(IllegalArgumentException.class,
				() -> service.inspect(snapshot, selected, mapping("  注文: {columns: {親ID: 数量}}\n")));
		assertThrows(IllegalArgumentException.class,
				() -> service.inspect(snapshot, selected, mapping("  顧客: {}\n  PUBLIC.顧客: {}\n")));
		var swapped = service.inspect(snapshot, selected, mapping("  注文: {columns: {親ID: 数量, 数量: 親ID}}\n"));
		assertEquals(List.of("数量", "親ID"),
				swapped.tables().getLast().columns().stream().map(column -> column.target()).toList());
	}

	@Test
	void rejectsChangedSchemasAndInvalidSelectionsAndRetainsExpressionWarning() throws Exception {
		var snapshot = schema();
		var empty = io.validate("formatVersion: 1\ntables: {}");
		var defaults = service.inspect(snapshot, List.of("顧客", "注文"), empty);
		assertEquals(2, defaults.tables().size());
		assertTrue(defaults.warnings().isEmpty());
		assertThrows(IllegalArgumentException.class, () -> service.inspect(snapshot, List.of(), empty));
		assertThrows(IllegalArgumentException.class, () -> service.inspect(snapshot, List.of(" "), empty));
		var renamed = service.inspect(snapshot, List.of("顧客"), mapping("  顧客: {targetTable: PEOPLE}\n"));
		assertTrue(renamed.warnings().stream().anyMatch(warning -> warning.contains("SCHEMA_MAPPING_EXPRESSIONS")));
		Files.writeString(snapshot.file(), Files.readString(snapshot.file()) + "\n<!-- changed -->");
		assertThrows(IllegalArgumentException.class, () -> service.inspect(snapshot, List.of("顧客"), empty));
	}
}
