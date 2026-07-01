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

import java.sql.Connection;
import java.sql.SQLException;

import com.sqlapp.jdbc.function.SQLConsumer;

public class CommitCountHolder {
	private long commitHandleCount;
	private long commitCount;
	private long commitSize;
	private final SQLConsumer<Connection> commitHandler;

	public CommitCountHolder(final long commitSize, SQLConsumer<Connection> commitHandler) {
		this.commitSize = commitSize;
		this.commitHandler = commitHandler;
		commitHandleCount = 0;
		commitCount = 0;
	}

	public long getCommitCount() {
		return commitCount;
	}

	public void countUp() {
		commitHandleCount++;
	}

	public long getCommitSize() {
		return commitSize;
	}

	public void setCommitSize(long commitSize) {
		this.commitSize = commitSize;
	}

	public boolean isCommit() {
		if (commitHandleCount + 1 >= commitSize) {
			return true;
		}
		return false;
	}

	public boolean commit(final Connection connection) throws SQLException {
		if (isCommit()) {
			commitInternal(connection);
			commitHandleCount = 0;
			commitCount++;
			return true;
		}
		countUp();
		return false;
	}

	private void commitInternal(final Connection connection) throws SQLException {
		if (commitHandler != null) {
			commitHandler.accept(connection);
		}
	}

	public boolean finalCommit(final Connection connection) throws SQLException {
		if (isFinalCommit()) {
			commitInternal(connection);
			commitHandleCount = 0;
			commitCount++;
			return true;
		}
		return false;
	}

	public boolean isFinalCommit() {
		if (commitCount > 0 && commitHandleCount > 0) {
			return true;
		}
		return false;
	}
}
