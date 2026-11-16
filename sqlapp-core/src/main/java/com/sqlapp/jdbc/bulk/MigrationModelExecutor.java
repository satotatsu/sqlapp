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
}
