/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.CheckConstraintReader;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.ExcludeConstraintReader;
import com.sqlapp.data.db.metadata.ForeignKeyConstraintReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.db.metadata.UniqueConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Statistics;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DoubleKeyMap;

/**
 * Postgresのテーブル情報読み込み
 * 
 * @author satoh
 * 
 */
public class PostgresTableReader extends TableReader {

	protected PostgresTableReader(Dialect dialect) {
		super(dialect);
	}

	private String[] relkind=new String[]{"r"};
	
	@Override
	protected List<Table> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		ParametersContext clone=context.clone();
		if (!CommonUtils.isEmpty(this.relkind)){
			clone.put("relkind", this.relkind);
		}
		final DoubleKeyMap<String,String,Table> result = CommonUtils.doubleKeyMap();
		execute(connection, node, clone, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Table table = createTable(rs);
				result.put(table.getSchemaName(), table.getName(), table);
			}
		});
		setInherits(connection, context, result);
		return result.toList();
	}

	protected void setInherits(Connection connection,
			ParametersContext context, final DoubleKeyMap<String,String,Table> map) {
		if (map.size()==0){
			return;
		}
		SqlNode node = getInheritsSqlSqlNode();
		ParametersContext clone=context.clone();
		clone.put(SCHEMA_NAME, map.keySet());
		clone.put(TABLE_NAME, map.secondKeySet());
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String psName = getString(rs, "parent_" + SCHEMA_NAME);
				String ptName = getString(rs, "parent_" + TABLE_NAME);
				Table table = map.get(getString(rs, SCHEMA_NAME),
						getString(rs, TABLE_NAME));
				Table pTable = map.get(psName, ptName);
				if (pTable == null) {
					pTable = new Table(ptName).setSchemaName(psName);
				}
				addInherits(table,pTable);
			}
		});
	}
	
	protected void addInherits(Table table, Table pTable) {
		table.getInherits().add(pTable);
	}
	
	protected Table createTable(ExResultSet rs) throws SQLException {
		Table obj = createTable(getString(rs, TABLE_NAME));
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		obj.setRemarks(getString(rs, "remarks"));
		obj.setId(getString(rs, "table_id"));
		this.setSpecifics(rs, "relhasoids", "oids", obj);
		Statistics.ROWS.setValue(rs, "n_live_tup", obj);
		Statistics.DATA_LENGTH.setValue(rs, "data_length", obj);
		//
		this.setStatistics(rs, "seq_scan", obj);
		this.setStatistics(rs, "seq_tup_read", obj);
		this.setStatistics(rs, "idx_scan", obj);
		this.setStatistics(rs, "idx_tup_fetch", obj);
		this.setStatistics(rs, "n_tup_ins", obj);
		this.setStatistics(rs, "n_tup_upd", obj);
		this.setStatistics(rs, "n_tup_del", obj);
		this.setStatistics(rs, "n_tup_hot_upd", obj);
		this.setStatistics(rs, "n_dead_tup", obj);
		this.setStatistics(rs, "n_mod_since_analyze", obj);
		this.setStatistics(rs, "last_vacuum", obj);
		this.setStatistics(rs, "last_autovacuum", obj);
		this.setStatistics(rs, "last_analyze", obj);
		this.setStatistics(rs, "last_autoanalyze", obj);
		this.setStatistics(rs, "vacuum_count", obj);
		this.setStatistics(rs, "autovacuum_count", obj);
		this.setStatistics(rs, "analyze_count", obj);
		this.setStatistics(rs, "autoanalyze_count", obj);
		return obj;
	}

	@Override
	protected void setMetadataDetail(Connection connection,
			ParametersContext context, List<Table> list) throws SQLException {
		super.setMetadataDetail(connection, context, list);
	}

	public void setRelkind(String... relkind){
		this.relkind=relkind;
	}
	
	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("tables.sql");
	}

	protected SqlNode getInheritsSqlSqlNode() {
		return getSqlNodeCache().getString("inherits.sql");
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new PostgresColumnReader(this.getDialect());
	}

	@Override
	protected IndexReader newIndexReader() {
		return new PostgresIndexReader(this.getDialect());
	}

	@Override
	protected UniqueConstraintReader newUniqueConstraintReader() {
		return new PostgresUniqueConstraintReader(this.getDialect());
	}

	@Override
	protected CheckConstraintReader newCheckConstraintReader() {
		return new PostgresCheckConstraintReader(this.getDialect());
	}

	@Override
	protected ForeignKeyConstraintReader newForeignKeyConstraintReader() {
		return new PostgresForeignKeyConstraintReader(this.getDialect());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.dialect.metadata.TableReader#newExcludeConstraintReader
	 * ()
	 */
	@Override
	protected ExcludeConstraintReader newExcludeConstraintReader() {
		return null;
	}

}
