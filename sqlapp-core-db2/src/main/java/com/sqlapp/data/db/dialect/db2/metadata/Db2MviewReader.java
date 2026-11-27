/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.ExcludeConstraintReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.db.metadata.MviewReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Mview;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/** Db2 LUW MQTs retain the server's full CREATE TABLE definition. */
public class Db2MviewReader extends MviewReader {
	protected Db2MviewReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Table> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		List<Table> result = new ArrayList<>();
		execute(connection, getSqlNodeCache().getString("mviews.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				result.add(createMview(rs));
			}
		});
		return result;
	}

	protected Mview createMview(ExResultSet rs) throws SQLException {
		Mview mview = new Mview(getString(rs, TABLE_NAME));
		mview.setDialect(getDialect());
		mview.setSchemaName(getString(rs, SCHEMA_NAME));
		mview.setDefinition(getString(rs, "TEXT"));
		mview.setRemarks(getString(rs, REMARKS));
		mview.setCreatedAt(rs.getTimestamp("CREATE_TIME"));
		mview.setLastAlteredAt(rs.getTimestamp("ALTER_TIME"));
		mview.setTableSpaceName(getString(rs, "TBSPACE"));
		mview.setIndexTableSpaceName(getString(rs, "INDEX_TBSPACE"));
		mview.setLobTableSpaceName(getString(rs, "LONG_TBSPACE"));
		mview.getSpecifics().put("REFRESH", getString(rs, "REFRESH"));
		mview.getSpecifics().put("QUALIFIER", getString(rs, "QUALIFIER"));
		mview.getSpecifics().put("FUNC_PATH", getString(rs, "FUNC_PATH"));
		mview.setValid("Y".equals(getString(rs, "VALID")));
		return mview;
	}

	@Override
	protected ColumnReader newColumnReader() {
		return getDialect().getCatalogReader().getSchemaReader().getTableReader().getColumnReader();
	}

	@Override
	protected IndexReader newIndexReader() {
		return new Db2IndexReader(getDialect());
	}

	@Override
	protected ExcludeConstraintReader newExcludeConstraintReader() {
		return null;
	}
}
