/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.SQLException;
import java.util.Iterator;

import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;

/**
 * Opens an ordered source after an opaque, durable keyset token.
 *
 * <p>The source must order rows by a unique, immutable, non-null key. The token
 * returned for a row must reopen the source strictly after that row. Tokens are
 * stored verbatim and are interpreted only by this source.</p>
 */
public interface BulkMigrationKeysetSource {
	/** Schema and target identity used to build migration chunks. */
	Table getTable();

	/**
	 * Returns a non-empty, stable fingerprint of every setting that affects token
	 * interpretation or row order.
	 */
	String getConfigurationFingerprint();

	/** Opens the source at its beginning when the token is {@code null}. */
	Iterator<Row> iterator(String resumeToken) throws SQLException;

	/** Returns a durable token representing the supplied row's complete key. */
	String resumeToken(Row row) throws SQLException;
}
