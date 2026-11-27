package com.sqlapp.data.db.dialect;

import java.util.Objects;

/**
 * A finding produced while converting a definition for another dialect.
 *
 * @param severity how the finding affects completion
 * @param code     stable machine-readable code
 * @param message  human-readable explanation
 */
public record MigrationDiagnostic(Severity severity, String code, String message) {

	/** Code of findings converted from unclassified string issues. */
	public static final String UNCLASSIFIED = "UNCLASSIFIED";

	public MigrationDiagnostic {
		Objects.requireNonNull(severity, "severity");
		Objects.requireNonNull(code, "code");
		Objects.requireNonNull(message, "message");
	}

	/** Severity of a finding. Only {@link #UNRESOLVED} blocks completion. */
	public enum Severity {
		/** Informational; no decision required. */
		INFO,
		/** The result differs from the source; the conversion still proceeds. */
		WARNING,
		/** A decision or correction is required. */
		UNRESOLVED
	}

	/** Whether this finding blocks completion. */
	public boolean isBlocking() {
		return severity == Severity.UNRESOLVED;
	}
}
