/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-saphana.
 *
 * sqlapp-core-saphana is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-saphana is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with sqlapp-core-saphana.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.saphana.metadata;

import static com.sqlapp.util.CommonUtils.list;

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
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Table.TableDataStoreType;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

public class SapHanaTableReader extends TableReader {

	protected SapHanaTableReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Table> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Table> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Table table = createTable(rs);
				result.add(table);
			}
		});
		return result;
	}

	protected Table createTable(ExResultSet rs) throws SQLException {
		Table table = createTable(getString(rs, TABLE_NAME));
		table.setSchemaName(getString(rs, SCHEMA_NAME));
		table.setId("" + rs.getLong("TABLE_OID"));
		table.setRemarks(getString(rs, "COMMENTS"));
		table.setTableDataStoreType(rs.getString("TABLE_TYPE"));
		setSpecifics(rs, table);
		setStatistics(rs, table);
		return table;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("tables.sql");
	}

	protected void setSpecifics(ExResultSet rs, Table table)
			throws SQLException {
		this.setSpecifics("IS_LOGGED", "TRUE".equalsIgnoreCase(rs.getString("IS_LOGGED")), table);
	}

	protected void setStatistics(ExResultSet rs, Table table)
			throws SQLException {
		setStatistics(rs, "FIXED_PART_SIZE", table);
		setStatistics(rs, "IS_SYSTEM_TABLE", table);
		setStatistics(rs, "IS_COLUMN_TABLE", table);
		setStatistics(rs, "IS_INSERT_ONLY", table);
		setStatistics(rs, "IS_TENANT_SHARED_DATA", table);
		setStatistics(rs, "IS_TENANT_SHARED_METADATA", table);
		setStatistics(rs, "SESSION_TYPE", table);
		setStatistics(rs, "IS_TEMPORARY", table);
		setStatistics(rs, "TEMPORARY_TABLE_TYPE", table);
		setStatistics(rs, "IS_USER_DEFINED_TYPE", table);
		setStatistics(rs, "USES_EXTKEY", table);
		setStatistics(rs, "AUTO_MERGE_ON", table);
		if (table.getTableDataStoreType() == TableDataStoreType.Column) {
			setStatistics(rs, "PARTITION_SPEC", table);
			setStatistics(rs, "USES_DIMFN_CACHE", table);
			setStatistics(rs, "IS_PUBLIC", table);
			setStatistics(rs, "COMPRESSED_EXTKEY", table);
			setStatistics(rs, "HAS_TEXT_FIELDS", table);
			setStatistics(rs, "USES_QUEUE_TABLE", table);
			setStatistics(rs, "IS_PRELOAD", table);
			setStatistics(rs, "IS_PARTIAL_PRELOAD", table);
		}
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new SapHanaColumnReader(this.getDialect());
	}

	@Override
	protected UniqueConstraintReader newUniqueConstraintReader() {
		return new SapHanaUniqueConstraintReader(this.getDialect());
	}

	@Override
	protected CheckConstraintReader newCheckConstraintReader() {
		return null;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.metadata.TableReader#newForeignKeyConstraintReader()
	 */
	@Override
	protected ForeignKeyConstraintReader newForeignKeyConstraintReader() {
		return new SapHanaForeignKeyConstraintReader(this.getDialect());
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.metadata.TableReader#newIndexReader()
	 */
	@Override
	protected IndexReader newIndexReader() {
		return new SapHanaIndexReader(this.getDialect());
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
