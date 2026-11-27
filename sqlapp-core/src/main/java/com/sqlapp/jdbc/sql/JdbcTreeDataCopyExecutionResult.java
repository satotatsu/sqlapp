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

import com.sqlapp.jdbc.sql.JdbcTreeDataExecutionResult.TableIdentity;

/**
 * Independent execution evidence for both sides of a copy operation, and the rows
 * read and copied per source/target table pair.
 */
public record JdbcTreeDataCopyExecutionResult(JdbcTreeDataExecutionResult source,
        JdbcTreeDataExecutionResult target, List<CopyCount> counts) {
	public JdbcTreeDataCopyExecutionResult {
		counts = List.copyOf(counts);
	}

	/**
	 * Rows of the source table read by the work and rows copied with newCopy to the
	 * target table. {@code verified} is true when the pair was checked by
	 * {@code verifyCounts}.
	 */
	public record CopyCount(TableIdentity source, TableIdentity target, long read, long copied,
			boolean verified) { }
}
