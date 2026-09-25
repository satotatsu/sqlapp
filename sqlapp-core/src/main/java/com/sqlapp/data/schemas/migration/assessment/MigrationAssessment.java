/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.migration.assessment;

import java.util.List;
import java.util.Objects;

/** Offline findings are evidence for review, never certification of a migration. */
public record MigrationAssessment(List<Finding> findings, List<Inventory> inventory) {
	public MigrationAssessment {
		findings = List.copyOf(findings);
		inventory = List.copyOf(inventory);
	}

	public enum Severity { BLOCKER, WARNING, REVIEW }
	public enum Evidence { SCHEMA, DOCUMENTED_RULE, MANUAL_CHECK }
	public enum Method { DIRECT_UPGRADE, LOGICAL_MIGRATION }

	/** Structured identity avoids ambiguity in names containing dots or quotes. */
	public record ObjectId(String catalog, String schema, String type, String name, String table) {
		public ObjectId(final String catalog, final String schema, final String type, final String name) {
			this(catalog, schema, type, name, null);
		}
	}
	public record Finding(String ruleId, Severity severity, Evidence evidence, ObjectId object,
			String reason, String action, String reference) {
		public Finding {
			Objects.requireNonNull(ruleId, "ruleId");
			Objects.requireNonNull(severity, "severity");
			Objects.requireNonNull(evidence, "evidence");
			Objects.requireNonNull(reason, "reason");
			Objects.requireNonNull(action, "action");
		}
	}
	/** Counts describe the supplied snapshot, not completeness of database extraction. */
	public record Inventory(String catalog, String schema, String type, int count) { }

	public boolean hasBlockers() {
		return findings.stream().anyMatch(finding -> finding.severity() == Severity.BLOCKER);
	}

	public boolean requiresReview() {
		return findings.stream().anyMatch(finding -> finding.severity() != Severity.BLOCKER);
	}
}
