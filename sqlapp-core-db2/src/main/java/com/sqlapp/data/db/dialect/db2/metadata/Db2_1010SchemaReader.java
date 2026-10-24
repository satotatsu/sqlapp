/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.MaskReader;
import com.sqlapp.data.db.metadata.TableReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import com.sqlapp.data.db.dialect.db2.Db2RowPermissions;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

public class Db2_1010SchemaReader extends Db2_980SchemaReader {

	protected Db2_1010SchemaReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected TableReader newTableReader() {
		return new Db2_1010TableReader(this.getDialect());
	}

	@Override
	protected MaskReader newMaskReader() {
		return new Db2_1010MaskReader(this.getDialect());
	}

	@Override
	protected void setSchemaAfter(Connection connection, Schema schema) {
		super.setSchemaAfter(connection, schema);
		readAccessControls(connection, schema);
	}

	protected void readAccessControls(Connection connection, Schema schema) {
		ParametersContext context = new ParametersContext();
		context.put("schemaName", schema.getName());
		var permissions = new ArrayList<Db2RowPermissions.Permission>();
		var tables = new ArrayList<Db2RowPermissions.AccessControl>();
		execute(connection, getSqlNodeCache().getString("rowPermissions.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String tableSchema = getString(rs, "TABSCHEMA"), tableName = getString(rs, "TABNAME");
				if (schema.getName().equals(tableSchema) && !schema.getTables().contains(tableName))
					return;
				permissions.add(new Db2RowPermissions.Permission(getString(rs, "CONTROLSCHEMA"),
						getString(rs, "CONTROLNAME"), tableSchema, tableName, getString(rs, "RULETEXT"),
						getString(rs, "TABCORRELATION"), flag(rs, "ENABLE"), flag(rs, "VALID"),
						getString(rs, "QUALIFIER"), getString(rs, "FUNC_PATH"), getString(rs, "REMARKS")));
			}
		});
		execute(connection, getSqlNodeCache().getString("accessControls.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				if (!schema.getName().equals(getString(rs, "TABSCHEMA"))
						|| !schema.getTables().contains(getString(rs, "TABNAME")))
					return;
				String control = getString(rs, "CONTROL");
				if (control == null || !java.util.Set.of("", "R", "C", "B").contains(control.trim()))
					throw new IllegalArgumentException("Invalid Db2 access-control state: " + control);
				tables.add(new Db2RowPermissions.AccessControl(getString(rs, "TABSCHEMA"), getString(rs, "TABNAME"),
						"R".equals(control) || "B".equals(control), "C".equals(control) || "B".equals(control)));
			}
		});
		Db2RowPermissions.set(schema, permissions, tables);
	}

	private boolean flag(ExResultSet rs, String name) throws SQLException {
		String value = getString(rs, name);
		if (!"Y".equals(value) && !"N".equals(value))
			throw new IllegalArgumentException("Invalid Db2 " + name + " flag: " + value);
		return "Y".equals(value);
	}

}
