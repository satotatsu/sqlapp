/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.schemas.migration.LegacyMigrationMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.ColumnAction;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.ColumnMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.RelationshipMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.TableMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.TransformationRecord;
import com.sqlapp.exceptions.CommandException;

class LegacyMigrationMappingTest {

	@TempDir
	File temporaryDirectory;

	@Test
	void testYamlRoundTripAndValidation() {
		LegacyMigrationMapping mapping = new LegacyMigrationMapping();
		mapping.getMigration().setId("sample-v1");
		TableMapping parent = table("table-parent", "PARENT");
		TableMapping child = table("table-child", "CHILD");
		child.setParent(new LegacyMigrationMapping.ParentMapping());
		child.getParent().setMappingId(parent.getId());
		mapping.getTables().add(parent);
		mapping.getTables().add(child);
		RelationshipMapping relationship = new RelationshipMapping();
		relationship.setId("rel-parent-child");
		relationship.setParentMappingId(parent.getId());
		relationship.setChildMappingId(child.getId());
		relationship.setParentIdPropagation(true);
		mapping.getRelationships().add(relationship);

		File yaml = new File(temporaryDirectory, "mapping.yaml");
		LegacyMigrationMappingIO io = new LegacyMigrationMappingIO();
		io.write(yaml, mapping);
		LegacyMigrationMapping restored = io.read(yaml);

		new LegacyMigrationMappingValidator().validate(restored);
		assertEquals("sample-v1", restored.getMigration().getId());
		assertEquals(2, restored.getTables().size());
		assertTrue(restored.getRelationships().getFirst().isParentIdPropagation());
	}

	@Test
	void testRejectUnknownParent() {
		LegacyMigrationMapping mapping = new LegacyMigrationMapping();
		TableMapping child = table("table-child", "CHILD");
		child.setParent(new LegacyMigrationMapping.ParentMapping());
		child.getParent().setMappingId("missing");
		mapping.getTables().add(child);

		assertThrows(CommandException.class, () -> new LegacyMigrationMappingValidator().validate(mapping));
	}

	@Test
	void testSchemaFingerprintDetectsDifferentFile() throws Exception {
		File expected = new File(temporaryDirectory, "expected.xml");
		File different = new File(temporaryDirectory, "different.xml");
		Files.writeString(expected.toPath(), "<schema name=\"A\"/>");
		Files.writeString(different.toPath(), "<schema name=\"B\"/>");
		LegacyMigrationMapping mapping = new LegacyMigrationMapping();
		LegacyMigrationMappingValidator validator = new LegacyMigrationMappingValidator();
		mapping.getSource().setSchemaFingerprint(validator.fingerprint(expected));

		validator.validateSourceFingerprint(mapping, expected);
		assertThrows(CommandException.class, () -> validator.validateSourceFingerprint(mapping, different));
	}

	@Test
	void testComposeTransformationSteps() {
		LegacyMigrationMapping existing = mapping("sha256:source", "sha256:middle");
		TableMapping original = table("table-original", "MIDDLE_TABLE");
		original.getSource().setTable("LEGACY_TABLE");
		ColumnMapping originalColumn = new ColumnMapping();
		originalColumn.setSource("LEGACY_COLUMN");
		originalColumn.setTarget("MIDDLE_COLUMN");
		original.getColumns().add(originalColumn);
		existing.getTables().add(original);
		existing.getTransformations().add(transformation(10, "First", "sha256:source", "sha256:middle"));

		LegacyMigrationMapping step = mapping("sha256:middle", "sha256:target");
		TableMapping transformed = table("table-middle", "FINAL_TABLE");
		transformed.getSource().setTable("MIDDLE_TABLE");
		ColumnMapping transformedColumn = new ColumnMapping();
		transformedColumn.setSource("MIDDLE_COLUMN");
		transformedColumn.setTarget("FINAL_COLUMN");
		transformedColumn.setAction(ColumnAction.CAST);
		transformed.getColumns().add(transformedColumn);
		step.getTables().add(transformed);
		step.getTransformations().add(transformation(10, "Second", "sha256:middle", "sha256:target"));

		LegacyMigrationMapping merged = new LegacyMigrationMappingMerger().merge(existing, step);

		assertEquals("sha256:source", merged.getSource().getSchemaFingerprint());
		assertEquals("sha256:target", merged.getTarget().getSchemaFingerprint());
		assertEquals("LEGACY_TABLE", merged.getTables().getFirst().getSource().getTable());
		assertEquals("FINAL_TABLE", merged.getTables().getFirst().getTarget().getTable());
		assertEquals("LEGACY_COLUMN", merged.getTables().getFirst().getColumns().getFirst().getSource());
		assertEquals("FINAL_COLUMN", merged.getTables().getFirst().getColumns().getFirst().getTarget());
		assertEquals(ColumnAction.CAST, merged.getTables().getFirst().getColumns().getFirst().getAction());
		assertEquals(20, merged.getTransformations().getLast().getSequence());
	}

	@Test
	void testRejectDiscontinuousMapping() {
		LegacyMigrationMapping existing = mapping("sha256:source", "sha256:middle");
		LegacyMigrationMapping step = mapping("sha256:other", "sha256:target");

		assertThrows(CommandException.class, () -> new LegacyMigrationMappingMerger().merge(existing, step));
	}

	@Test
	void testAtomicReplacementLeavesNoTemporaryFile() {
		LegacyMigrationMapping mapping = new LegacyMigrationMapping();
		File yaml = new File(temporaryDirectory, "atomic.yaml");
		LegacyMigrationMappingIO io = new LegacyMigrationMappingIO();
		io.write(yaml, mapping);
		mapping.getMigration().setId("updated");
		io.write(yaml, mapping);

		assertEquals("updated", io.read(yaml).getMigration().getId());
		assertTrue(!new File(temporaryDirectory, "atomic.yaml.tmp").exists());
	}

	private TableMapping table(String id, String name) {
		TableMapping table = new TableMapping();
		table.setId(id);
		table.getTarget().setTable(name);
		return table;
	}

	private LegacyMigrationMapping mapping(String sourceFingerprint, String targetFingerprint) {
		LegacyMigrationMapping mapping = new LegacyMigrationMapping();
		mapping.getSource().setSchemaFingerprint(sourceFingerprint);
		mapping.getTarget().setSchemaFingerprint(targetFingerprint);
		return mapping;
	}

	private TransformationRecord transformation(int sequence, String command, String input, String output) {
		TransformationRecord record = new TransformationRecord();
		record.setSequence(sequence);
		record.setCommand(command);
		record.setStatus("SUCCESS");
		record.setInputFingerprint(input);
		record.setOutputFingerprint(output);
		return record;
	}
}
