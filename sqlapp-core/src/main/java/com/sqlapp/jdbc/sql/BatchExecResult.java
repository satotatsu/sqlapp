/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;

import com.sqlapp.jdbc.sql.JdbcBatchIterateHander.ValueHolder;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;

public class BatchExecResult {
	private final SqlNode sqlNode;
	private final PreparedStatement statement;
	private LocalDateTime start = LocalDateTime.now();
	private LocalDateTime end;
	private long lastRowIndex;
	private long startTimeMillis = System.currentTimeMillis();
	private long endTimeMillis;

	private int[] result;
	private List<ValueHolder> values;
	private List<GeneratedKeyInfo> generatedKeys;

	public BatchExecResult(SqlNode sqlNode, PreparedStatement statement, int batchSize) {
		this.sqlNode = sqlNode;
		this.statement = statement;
		values = CommonUtils.list(batchSize);
	}

	protected void setEnd(long lastRowIndex, int[] result, List<GeneratedKeyInfo> generatedKeys) {
		this.setLastRowIndex(lastRowIndex);
		this.result = result;
		this.generatedKeys = generatedKeys;
		this.endTimeMillis = System.currentTimeMillis();
		this.end = LocalDateTime.now();
	}

	public int[] getResult() {
		return result;
	}

	public List<GeneratedKeyInfo> getGeneratedKeys() {
		return generatedKeys;
	}

	public List<ValueHolder> getValues() {
		return values;
	}

	public void setValues(List<ValueHolder> values) {
		this.values = values;
	}

	public long getLastRowIndex() {
		return lastRowIndex;
	}

	private void setLastRowIndex(long counter) {
		this.lastRowIndex = counter;
	}

	public void setStart(LocalDateTime start) {
		this.start = start;
	}

	public long getMillis() {
		return endTimeMillis - startTimeMillis;
	}

	public LocalDateTime getStart() {
		return start;
	}

	public LocalDateTime getEnd() {
		return end;
	}

	public SqlNode getSqlNode() {
		return sqlNode;
	}

	public PreparedStatement getStatement() {
		return statement;
	}
}
