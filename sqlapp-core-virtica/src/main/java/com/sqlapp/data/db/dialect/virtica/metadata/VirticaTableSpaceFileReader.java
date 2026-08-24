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
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.TableSpaceFile;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Reads the node paths that comprise a labeled Vertica storage location. */
public class VirticaTableSpaceFileReader extends TableSpaceFileReader {

	protected VirticaTableSpaceFileReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<TableSpaceFile> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("tableSpaceFiles.sql");
		List<TableSpaceFile> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				result.add(createFile(rs));
			}
		});
		return result;
	}

	protected TableSpaceFile createFile(ExResultSet rs) throws SQLException {
		TableSpaceFile file = new TableSpaceFile(getString(rs, "LOCATION_ID"),
				getString(rs, "LOCATION_PATH"));
		file.setTableSpaceName(getString(rs, "LOCATION_LABEL"));
		setSpecifics(rs, "NODE_NAME", file);
		setSpecifics(rs, "LOCATION_USAGE", file);
		return file;
	}
}
