/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.db2.metadata;

import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.properties.StatisticsProperty;

public class Db2_970TableReader extends Db2_950TableReader {

	protected Db2_970TableReader(Dialect dialect) {
		super(dialect);
	}

	protected Table createTable(ExResultSet rs) throws SQLException {
		Table table=super.createTable(rs);
		return table;
	}
	
	protected SqlNode getPartitionSqlNode() {
		return getSqlNodeCache().getString("partitions970.sql");
	}
	
	protected Partition readPartition(ExResultSet rs, Table table) throws SQLException{
		Partition partition=super.readPartition(rs, table);
		String indexTableSpace = getString(rs,"INDEX_TABLE_SPACE");
		partition.setIndexTableSpaceName(indexTableSpace);
		this.setStatistics(rs, "CARD", partition);
		this.setStatistics(rs, "OVERFLOW", partition);
		this.setStatistics(rs, "NPAGES", partition);
		this.setStatistics(rs, "FPAGES", partition);
		this.setStatistics(rs, "ACTIVE_BLOCKS", partition);
		this.setStatistics(rs, "AVGROWSIZE", partition);
		this.setStatistics(rs, "PCTROWSCOMPRESSED", partition);
		this.setStatistics(rs, "PCTPAGESAVED", partition);
		this.setStatistics(rs, "AVGCOMPRESSEDROWSIZE", partition);
		this.setStatistics(rs, "AVGROWCOMPRESSIONRATIO", partition);
		this.setStatistics(rs, "STATS_TIME", partition);
		this.setStatistics(rs, "LASTUSED", partition);
		return partition;
	}
	
	@Override
	protected void setStatistics(ResultSet rs, String key,  StatisticsProperty<?> obj) throws SQLException{
		Object ret=rs.getObject(key);
		if (ret instanceof Number){
			Number val=Number.class.cast(ret);
			this.setStatistics(key, val, obj, val!=null&&val.longValue()!=-1);
		} else{
			this.setStatistics(key, ret, obj);
		}
	}
}
