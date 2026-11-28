/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;

import com.sqlapp.data.schemas.migration.MigrationModel;
import com.sqlapp.data.schemas.migration.MigrationModelSource;
import com.sqlapp.data.schemas.migration.MigrationModelUnitTest;

/**
 * Dialect-owned SQL for building SELECT-defined models inside one database
 * engine.
 */
public interface MigrationModelExecutor {

	/** Quoted identifier for a model, source or column name. */
	String identifier(String name);

	/** Relation expression under which the model is created and referenced. */
	String relation(MigrationModel model);

	/** Relation expression that reads a source table or file. */
	String sourceRelation(MigrationModelSource source);

	/** Inline relation expression for a unit-test fixture. */
	String fixtureRelation(MigrationModelUnitTest.Fixture fixture);

	/**
	 * Creates or replaces the model relation from its resolved SELECT. The
	 * caller owns the transaction.
	 *
	 * @param selectSql model SQL whose references are already resolved; it may
	 *                  still contain sqlapp comment-template parameters
	 */
	void materialize(Connection connection, MigrationModel model, String selectSql) throws SQLException;

	/**
	 * State of the table of an incremental model.
	 *
	 * @param exists      a relation with the model name exists
	 * @param fingerprint lineage fingerprint recorded with the table, or null when
	 *                    the relation is not a table built by an incremental model
	 *                    of this format
	 */
	record IncrementalState(boolean exists, String fingerprint) {
	}

	/** Reads the state of the table of an incremental model. */
	default IncrementalState incrementalState(final Connection connection, final MigrationModel model)
			throws SQLException {
		throw new UnsupportedOperationException("Incremental models are not supported by this engine.");
	}

	/**
	 * Builds or adds to the table of an incremental model and records the lineage
	 * fingerprint with it, in the caller's transaction.
	 *
	 * @param selectSql   resolved model SQL
	 * @param incremental true to merge the rows into the existing table by
	 *                    primary key, false to replace the table with all rows
	 * @param fingerprint lineage fingerprint to record
	 */
	default void materializeIncremental(final Connection connection, final MigrationModel model,
			final String selectSql, final boolean incremental, final String fingerprint) throws SQLException {
		throw new UnsupportedOperationException("Incremental models are not supported by this engine.");
	}
}
