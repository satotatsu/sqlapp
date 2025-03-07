/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.firebird.metadata;

import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Firebirdのテーブル読み込み
 * 
 * @author satoh
 * 
 */
public class Firebird30TableReader extends FirebirdTableReader {

	protected Firebird30TableReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new Firebird30ColumnReader(this.getDialect());
	}
	
	@Override
	protected SqlNode getSqlSqlNode(final ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("tables30.sql");
	}
	
	@Override
	protected Table createTable(ExResultSet rs) throws SQLException {
		Table obj = super.createTable(rs);
		this.setStatistics(rs, "MON$RECORD_SEQ_READS", obj);
		this.setStatistics(rs, "MON$RECORD_IDX_READS", obj);
		this.setStatistics(rs, "MON$RECORD_INSERTS", obj);
		this.setStatistics(rs, "MON$RECORD_UPDATES", obj);
		this.setStatistics(rs, "MON$RECORD_DELETES", obj);
		this.setStatistics(rs, "MON$RECORD_BACKOUTS", obj);
		this.setStatistics(rs, "MON$RECORD_PURGES", obj);
		this.setStatistics(rs, "MON$RECORD_EXPUNGES", obj);
		this.setStatistics(rs, "MON$RECORD_LOCKS", obj);
		this.setStatistics(rs, "MON$RECORD_WAITS", obj);
		this.setStatistics(rs, "MON$RECORD_CONFLICTS", obj);
		this.setStatistics(rs, "MON$BACKVERSION_READS", obj);
		this.setStatistics(rs, "MON$FRAGMENT_READS", obj);
		this.setStatistics(rs, "MON$RECORD_RPT_READS", obj);
		return obj;
	}
}
