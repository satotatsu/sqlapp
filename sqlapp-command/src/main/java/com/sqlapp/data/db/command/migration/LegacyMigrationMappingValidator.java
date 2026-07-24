/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import com.sqlapp.data.schemas.migration.LegacyMigrationMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.RelationshipMapping;
import com.sqlapp.data.schemas.migration.LegacyMigrationMapping.TableMapping;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.MessageDigests;

/**
 * Validates mapping structure and protects mappings from being applied to a
 * different schema artifact.
 */
public class LegacyMigrationMappingValidator {

	public void validate(LegacyMigrationMapping mapping) {
		if (mapping == null) {
			throw new CommandException("legacy migration mapping is required.");
		}
		if (!LegacyMigrationMapping.FORMAT.equals(mapping.getFormat())) {
			throw new CommandException("Unsupported legacy migration mapping format: " + mapping.getFormat());
		}
		if (mapping.getVersion() != LegacyMigrationMapping.CURRENT_VERSION) {
			throw new CommandException("Unsupported legacy migration mapping version: " + mapping.getVersion());
		}
		Set<String> ids = new HashSet<>();
		for (TableMapping table : mapping.getTables()) {
			if (table.getId() == null || table.getId().isBlank()) {
				throw new CommandException("Every table mapping requires an id.");
			}
			if (!ids.add(table.getId())) {
				throw new CommandException("Duplicate table mapping id: " + table.getId());
			}
			if (table.getTarget() == null || table.getTarget().getTable() == null
					|| table.getTarget().getTable().isBlank()) {
				throw new CommandException("Every table mapping requires target.table: " + table.getId());
			}
		}
		for (TableMapping table : mapping.getTables()) {
			if (table.getParent() != null && !ids.contains(table.getParent().getMappingId())) {
				throw new CommandException("Unknown parent mapping id: " + table.getParent().getMappingId());
			}
		}
		Set<String> relationshipIds = new HashSet<>();
		for (RelationshipMapping relationship : mapping.getRelationships()) {
			if (relationship.getId() == null || relationship.getId().isBlank()
					|| !relationshipIds.add(relationship.getId())) {
				throw new CommandException("Relationship ids must be non-empty and unique: " + relationship.getId());
			}
			if (!ids.contains(relationship.getParentMappingId()) || !ids.contains(relationship.getChildMappingId())) {
				throw new CommandException("Relationship refers to an unknown table mapping: " + relationship.getId());
			}
		}
	}

	public void validateSourceFingerprint(LegacyMigrationMapping mapping, File sourceSchema) {
		validateFingerprint("source", mapping.getSource().getSchemaFingerprint(), sourceSchema);
	}

	public void validateTargetFingerprint(LegacyMigrationMapping mapping, File targetSchema) {
		validateFingerprint("target", mapping.getTarget().getSchemaFingerprint(), targetSchema);
	}

	public String fingerprint(File file) {
		return "sha256:" + MessageDigests.SHA256.checksumAsString(file);
	}

	private void validateFingerprint(String endpoint, String expected, File file) {
		if (expected == null || expected.isBlank()) {
			throw new CommandException(endpoint + " schema fingerprint is not defined.");
		}
		String actual = fingerprint(file);
		if (!expected.equalsIgnoreCase(actual)) {
			throw new CommandException(endpoint + " schema fingerprint mismatch: expected=" + expected
					+ ", actual=" + actual + ", file=" + file);
		}
	}
}
