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
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.RelationshipMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.TableMapping;
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

	private TableMapping table(String id, String name) {
		TableMapping table = new TableMapping();
		table.setId(id);
		table.getTarget().setTable(name);
		return table;
	}
}
