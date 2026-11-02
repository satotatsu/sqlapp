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
	@Test
	void previewsTargetBuilderDefinitionsWithoutChangingSchemaOrMapping() throws Exception {
		var snapshot = schema();
		byte[] before = Files.readAllBytes(snapshot.file());
		var names = mapping("  顧客: {columns: {ID: OWNER_ID}}\n");
		var target = com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect("PostgreSQL", 16, 0, null);
		var result = service.inspect(snapshot, List.of("顧客"), names, target);
		var column = result.tables().getFirst().columns().getFirst();
		assertTrue(column.targetDefinition().contains("NOT NULL"));
		assertTrue(column.targetDefinition().toUpperCase(Locale.ROOT).contains("INT"));
		assertEquals("OWNER_ID", column.target());
		assertTrue(column.primaryKey());
		assertFalse(column.nullable());
		assertNull(column.previewIssue());
		assertEquals(target.getClass().getName(), result.targetDialect());
		assertEquals(service.inspect(snapshot, List.of("顧客"), names).mappingFingerprint(), result.mappingFingerprint());
		assertArrayEquals(before, Files.readAllBytes(snapshot.file()));
		try (var files = Files.list(directory)) {
			assertEquals(Set.of("schema.xml"),
					files.map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet()));
		}
	}

	@Test
	void sourceDoubleUsesPostgresBuilderDefinitionRatherThanSourceNativeSpelling() throws Exception {
		var schema = new Schema("PUBLIC");
		var table = new Table("価格");
		table.getColumns()
				.add(new Column("金額").setDataType(DataType.DOUBLE).setDataTypeName("DOUBLE").setDefaultValue("0"));
		schema.getTables().add(table);
		var file = directory.resolve("double.xml");
		schema.writeXml(file.toFile());
		var result = service.inspect(new BulkMigrationJobDraftBuilder().inspect(file), List.of("価格"),
				new TransferNameMappingIO.Configuration(1, Map.of()),
				com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect("PostgreSQL", 16, 0, null));
		var column = result.tables().getFirst().columns().getFirst();
		assertEquals("DOUBLE", column.sourceType());
		assertTrue(column.targetDefinition().toUpperCase(Locale.ROOT).contains("DOUBLE PRECISION"));
		assertTrue(column.targetDefinition().contains("DEFAULT 0"));
	}

	@Test
	void unsupportedTargetPreviewRemainsVisibleWithoutLosingSourceMetadata() throws Exception {
		var target = new com.sqlapp.data.db.dialect.Dialect(() -> null) {
			@Override
			public com.sqlapp.util.AbstractSqlBuilder<?> createSqlBuilder() {
				return new com.sqlapp.util.SqlBuilder(this) {
					@Override
					public com.sqlapp.util.SqlBuilder definition(Column column, boolean withRemarks) {
						throw new UnsupportedOperationException("test builder limitation\n" + "😀".repeat(400));
					}
				};
			}
		};
		var result = service.inspect(schema(), List.of("顧客"), new TransferNameMappingIO.Configuration(1, Map.of()),
				target);
		var column = result.tables().getFirst().columns().getFirst();
		assertNull(column.targetDefinition());
		assertTrue(column.previewIssue().contains("UnsupportedOperationException"));
		assertTrue(column.previewIssue().contains("test builder limitation"));
		assertFalse(column.previewIssue().contains("\n"));
		assertTrue(java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(column.previewIssue()));
		assertTrue(column.previewIssue().codePointCount(0, column.previewIssue().length()) < 550);
		assertTrue(column.primaryKey());
		assertEquals("INT", column.sourceType());
	}

	@TempDir
	Path directory;
	private final TransferNameMappingInspectionService service = new TransferNameMappingInspectionService();
	private final TransferNameMappingIO io = new TransferNameMappingIO();

	@Test
	void generatedColumnPreviewExplainsRequiredPostgresVersionAndKeepsOtherColumns() throws Exception {
		var model = new Schema("PUBLIC");
		var table = new Table("CALCULATIONS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		table.getColumns()
				.add(new Column("CALCULATED").setDataType(DataType.INT).setFormula("1 + 1").setFormulaPersisted(true));
		model.getTables().add(table);
		var file = directory.resolve("generated.xml");
		model.writeXml(file.toFile());
		var snapshot = new BulkMigrationJobDraftBuilder().inspect(file);
		var mapping = new TransferNameMappingIO.Configuration(1, Map.of());
		var resolver = com.sqlapp.data.db.dialect.DialectResolver.getInstance();
		var older = service.inspect(snapshot, List.of("CALCULATIONS"), mapping,
				resolver.getDialect("PostgreSQL", 11, 0, null));
		assertNotNull(older.tables().getFirst().columns().getFirst().targetDefinition());
		var unavailable = older.tables().getFirst().columns().getLast();
		assertNull(unavailable.targetDefinition());
		assertTrue(unavailable.previewIssue().contains("PostgreSQL 12 or later"));
		var supported = service.inspect(snapshot, List.of("CALCULATIONS"), mapping,
				resolver.getDialect("PostgreSQL", 12, 0, null));
		assertNull(supported.tables().getFirst().columns().getLast().previewIssue());
		assertTrue(supported.tables().getFirst().columns().getLast().targetDefinition().contains("GENERATED"));
		assertEquals("PUBLIC.CALCULATIONS", older.tables().getFirst().mappingSource());
	}

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
		assertEquals(List.of("PUBLIC.顧客", "注文"), result.tables().stream().map(table -> table.mappingSource()).toList());
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
