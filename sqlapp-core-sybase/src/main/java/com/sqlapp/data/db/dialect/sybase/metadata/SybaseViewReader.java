/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sybase.
 *
 * sqlapp-core-sybase is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sybase is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sybase.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sybase.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.information_schema.metadata.AbstractISViewReader;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.ExcludeConstraintReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * SqlServerのビュー読み込み
 * 
 * @author satoh
 * 
 */
public class SybaseViewReader extends AbstractISViewReader {

	protected SybaseViewReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Table> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("views.sql");
		List<Table> result = list();
		Map<Table, StringBuilder> definitions = new LinkedHashMap<>();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String name = getString(rs, TABLE_NAME);
				Table obj = result.isEmpty() ? null : result.get(result.size() - 1);
				if (obj == null || !name.equals(obj.getName())) {
					obj = superCreateTable(rs);
					result.add(obj);
					definitions.put(obj, new StringBuilder());
				}
				definitions.get(obj).append(getString(rs, "view_definition"));
			}
		});
		definitions.forEach((obj, definition) -> setDefinition(obj, definition.toString()));
		return result;
	}

	private Table superCreateTable(ExResultSet rs) throws SQLException {
		return createTable(rs);
	}

	private void setDefinition(Table obj, String definition) {
		if (this.getReaderOptions().isReadDefinition()) {
			obj.setDefinition(definition);
		}
		if (this.getReaderOptions().isReadStatement()) {
			obj.setStatement(SybaseUtils.getViewStatement(definition));
		}
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new SybaseColumnReader(this.getDialect());
	}

	@Override
	protected Table createTable(ExResultSet rs) throws SQLException {
		Table obj = super.createTable(getString(rs, TABLE_NAME));
		obj.setCatalogName(getString(rs, TABLE_CATALOG));
		obj.setSchemaName(getString(rs, TABLE_SCHEMA));
		// TODO
		// table.setCheckOption(getString(rs, "CHECK_OPTION"));
		// table.setUpdatable(getString(rs, "IS_UPDATABLE"));
		String difinition = getString(rs, "view_definition");
		if (this.getReaderOptions().isReadDefinition()) {
			obj.setDefinition(difinition);
		}
		if (this.getReaderOptions().isReadStatement()) {
			obj.setStatement(SybaseUtils.getViewStatement(difinition));
		}
		return obj;
	}

	@Override
	protected IndexReader newIndexReader() {
		return null;
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
