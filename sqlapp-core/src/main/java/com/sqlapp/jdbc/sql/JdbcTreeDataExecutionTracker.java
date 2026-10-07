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

import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcTreeDataExecutionResult.*;

final class JdbcTreeDataExecutionTracker {
	private record Key(TableIdentity table, SqlType operation) { }
	private final Map<Key, long[]> counts = new LinkedHashMap<>();
	long completedBatches;
	long committedBatches;
	long commits;
	boolean dirty;
	FailureContext context = new FailureContext(Phase.BUSINESS, null, null);

	static TableIdentity identity(Table table) {
		return new TableIdentity(table.getCatalogName(), table.getSchemaName(), table.getName());
	}

	void sql(Table table, SqlType operation) {
		context = new FailureContext(Phase.SQL, identity(table), operation);
	}

	void business() { context = new FailureContext(Phase.BUSINESS, null, null); }

	void record(Table table, SqlType operation, int[] result, int expected) {
		if (result == null) result = new int[0];
		long[] values = counts.computeIfAbsent(new Key(identity(table), operation), key -> new long[8]);
		for (int count : result) {
			if (count >= 0) values[0] += count;
			else if (count == Statement.SUCCESS_NO_INFO) values[1]++;
			else if (count == Statement.EXECUTE_FAILED) values[2]++;
			else values[3]++;
		}
		values[3] += Math.max(0, expected - result.length);
		dirty = true;
	}

	void record(Table table, SqlType operation, long count) {
		long[] values = counts.computeIfAbsent(new Key(identity(table), operation), key -> new long[8]);
		if (count >= 0) values[0] += count;
		else if (count == Statement.SUCCESS_NO_INFO) values[1]++;
		else values[3]++;
		dirty = true;
	}

	void committed() {
		counts.values().forEach(values -> System.arraycopy(values, 0, values, 4, 4));
		committedBatches = completedBatches;
		commits++;
		dirty = false;
	}

	private Counts snapshot(long[] values, int offset) {
		return new Counts(values[offset], values[offset + 1], values[offset + 2], values[offset + 3]);
	}

	JdbcTreeDataExecutionResult snapshot(boolean successful, TransactionOutcome outcome) {
		return new JdbcTreeDataExecutionResult(successful, counts.entrySet().stream()
				.map(entry -> new OperationResult(entry.getKey().table(), entry.getKey().operation(),
						snapshot(entry.getValue(), 0), snapshot(entry.getValue(), 4))).toList(),
				completedBatches, committedBatches, commits, outcome, successful ? null : context);
	}
}
