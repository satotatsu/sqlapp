/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.oracle.metadata;


import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.MviewLog;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
/**
 * Oracle11gR2のマテビューログファクトリ
 * @author satoh
 *
 */
public class Oracle11gR2MviewLogReader extends OracleMviewLogReader{

	protected Oracle11gR2MviewLogReader(Dialect dialect) {
		super(dialect);
	}


	protected MviewLog createMviewLog(ExResultSet rs, ResultSetNextHandler handler) throws SQLException{
		MviewLog obj=super.createMviewLog(rs, handler);
		obj.setPurgeInterval(getString(rs, "PURGE_INTERVAL"));
		obj.setPurgeAsynchronous("YES".equalsIgnoreCase(getString(rs, "PURGE_ASYNCHRONOUS")));
		obj.setPurgeDeferred("YES".equalsIgnoreCase(getString(rs, "PURGE_DEFERRED")));
		obj.setPurgeStart(rs.getTimestamp("PURGE_START"));
		obj.setCommitScnBased("YES".equalsIgnoreCase(getString(rs, "COMMIT_SCN_BASED")));
		this.setStatistics(rs, "LAST_PURGE_DATE", obj);
		this.setStatistics(rs, "LAST_PURGE_STATUS", obj);
		this.setStatistics(rs, "NUM_ROWS_PURGED", obj);
		return obj;
	}
}
