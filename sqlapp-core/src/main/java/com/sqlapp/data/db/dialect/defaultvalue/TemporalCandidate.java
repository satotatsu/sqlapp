package com.sqlapp.data.db.dialect.defaultvalue;

/**
 * A current date/time expression a target dialect can use as a column default.
 *
 * @param sql       SQL text for the DEFAULT clause
 * @param semantics semantics of the expression when stored in the target column
 */
public record TemporalCandidate(String sql, TemporalSemantics semantics) {
	public TemporalCandidate {
		java.util.Objects.requireNonNull(sql, "sql");
		java.util.Objects.requireNonNull(semantics, "semantics");
	}
}
