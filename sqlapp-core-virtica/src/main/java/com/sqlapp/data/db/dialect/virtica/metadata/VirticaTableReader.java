/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-virtica.
 *
 * sqlapp-core-virtica is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-virtica is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-virtica.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.virtica.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

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
import com.sqlapp.data.schemas.Table.TableType;

/**
 * Virtica Table Reader
 * 
 * @author satoh
 * 
 */
public class VirticaTableReader extends TableReader {

	protected VirticaTableReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Table> doGetAll(final Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
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
		Table obj = createTable(getString(rs, TABLE_NAME));
		obj.setDialect(getDialect());
		obj.setSchemaName(getString(rs, "TABLE_SCHEMA"));
		obj.setRemarks(getString(rs, "remarks"));
		obj.setCreatedAt(rs.getTimestamp("CREATE_TIME"));
		obj.setId(rs.getString("TABLE_ID"));
		if (rs.getBoolean("IS_TEMP_TABLE")){
			obj.setTableType(TableType.Temporary);
		}
		if (rs.getBoolean("IS_FLEXTABLE")){
			obj.setTableType(TableType.Flex);
		}
		obj.setTableDataStoreType(TableDataStoreType.Column);
		obj.setCreatedAt(rs.getTimestamp("CREATE_TIME"));
		obj.setRemarks(this.getString(rs, "COMMENT"));
		setSpecifics(rs, "commit_action", obj);
		setSpecifics(rs, "PARTITION_EXPRESSION", obj);
		setSpecifics(rs, "TABLE_DEFINITION", obj);
		return obj;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("tables.sql");
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new VirticaColumnReader(this.getDialect());
	}

	@Override
	protected IndexReader newIndexReader() {
		return null;
	}

	@Override
	protected UniqueConstraintReader newUniqueConstraintReader() {
		return new VirticaUniqueConstraintReader(this.getDialect());
	}

	@Override
	protected CheckConstraintReader newCheckConstraintReader() {
		return null;
	}

	@Override
	protected ForeignKeyConstraintReader newForeignKeyConstraintReader() {
		return new VirticaForeignKeyConstraintReader(this.getDialect());
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
