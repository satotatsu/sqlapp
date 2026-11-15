/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.migration.legacy;

import java.util.List;

/**
 * Result of {@link JdbcTreeStagingLoader#load()}.
 *
 * @param roots    staged root rows processed
 * @param dataSets row counts per load data set, in load order
 */
public record JdbcTreeStagingLoadResult(long roots, List<DataSetCount> dataSets) {
	public JdbcTreeStagingLoadResult {
		dataSets = List.copyOf(dataSets);
	}

	/**
	 * Row counts of one load data set.
	 *
	 * @param dataSetId load data set id
	 * @param read      staging rows read
	 * @param copied    target rows written to JDBC
	 * @param inserted  rows the JDBC driver reported as inserted, or {@code null}
	 *                  when the operation mode does not insert rows one to one
	 *                  (MERGE, REPLACE) or the driver did not report every count
	 * @param verified  true when read, copied and inserted rows were verified to be
	 *                  equal before every commit
	 */
	public record DataSetCount(String dataSetId, long read, long copied, Long inserted, boolean verified) {
		/**
		 * Rows not inserted by INSERT_IGNORE because the target row already existed.
		 */
		public long ignored() {
			return inserted == null ? 0 : copied - inserted;
		}
	}
}
