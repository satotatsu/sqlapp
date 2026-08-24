/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TableSpaceFileReader;
import com.sqlapp.data.db.metadata.TableSpaceReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.TableSpace;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Reads labeled Vertica storage locations as logical table spaces. */
public class VirticaTableSpaceReader extends TableSpaceReader {

	protected VirticaTableSpaceReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<TableSpace> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("tableSpaces.sql");
		List<TableSpace> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				result.add(new TableSpace(getString(rs, "LOCATION_LABEL")));
			}
		});
		return result;
	}

	@Override
	protected TableSpaceFileReader newTableSpaceFileReader() {
		return new VirticaTableSpaceFileReader(this.getDialect());
	}
}
