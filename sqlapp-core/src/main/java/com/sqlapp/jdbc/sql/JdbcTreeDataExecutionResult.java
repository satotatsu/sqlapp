/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.jdbc.sql;

import java.util.List;
import com.sqlapp.data.db.sql.SqlType;

/** Immutable JDBC-reported execution evidence. Counts do not include triggers or implicit cascades. */
public record JdbcTreeDataExecutionResult(boolean successful, List<OperationResult> operations,
		long completedRootBatches, long committedRootBatches, long commits,
		TransactionOutcome remainingTransaction, FailureContext failure) {
	public JdbcTreeDataExecutionResult {
		operations = List.copyOf(operations);
	}

	public enum TransactionOutcome { NOT_REQUIRED, COMMITTED, ROLLED_BACK, UNKNOWN }
	public enum Phase { BUSINESS, SQL, COMMIT, CLEANUP }
	public record TableIdentity(String catalog, String schema, String table) { }
	public record FailureContext(Phase phase, TableIdentity table, SqlType operation) { }
	/** Known rows are a lower bound when unknownCounts or unreportedCounts are nonzero. */
	public record Counts(long knownAffectedRows, long unknownCounts, long failedCounts, long unreportedCounts) { }
	public record OperationResult(TableIdentity table, SqlType operation, Counts executed, Counts committed) { }
}
