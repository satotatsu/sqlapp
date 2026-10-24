/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.mysql.metadata.MySqlViewReader;
import com.sqlapp.data.db.dialect.mysql.sql.MySqlCreateViewFactory;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.View;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class MariadbViewAlgorithmTest {
	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	void readsAvailableAlgorithmInOneBatchAndRecreates(boolean hasAlgorithm) {
		var dialect = DialectResolver.getInstance().getDialect("MariaDB", 10, 11, 0);
		assertInstanceOf(MySqlViewReader.class, dialect.getCatalogReader().getSchemaReader().getViewReader());
		int[] queries = {0};
		var reader = new MySqlViewReader(dialect) {
			java.util.List<com.sqlapp.data.schemas.Table> read() {
				return doGetAll(null, new ParametersContext(), new ProductVersionInfo()
						.setMajorVersion(10).setMinorVersion(11).setRevision(0));
			}
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode sql, ParametersContext context,
					ResultSetNextHandler handler) {
				queries[0]++;
				assertTrue(sql.toString().contains("information_schema.views"));
				try (var rows = RowSetProvider.newFactory().createCachedRowSet()) {
					String[] columns = {"TABLE_NAME", "TABLE_SCHEMA", "TABLE_CATALOG", "VIEW_DEFINITION", "ALGORITHM"};
					var metadata = new RowSetMetaDataImpl();
					metadata.setColumnCount(hasAlgorithm ? 5 : 4);
					for (int i = 1; i <= metadata.getColumnCount(); i++) {
						metadata.setColumnName(i, columns[i - 1]);
						metadata.setColumnLabel(i, columns[i - 1]);
						metadata.setColumnType(i, Types.VARCHAR);
					}
					rows.setMetaData(metadata);
					for (String algorithm : new String[] {"MERGE", "TEMPTABLE", "UNDEFINED"}) {
						rows.moveToInsertRow();
						rows.updateString(1, "v_" + algorithm);
						rows.updateString(2, "app");
						rows.updateString(3, "def");
						rows.updateString(4, "SELECT 1");
						if (hasAlgorithm) rows.updateString(5, algorithm);
						rows.insertRow();
						rows.moveToCurrentRow();
					}
					rows.beforeFirst();
					var result = new ExResultSet(rows);
					while (rows.next()) handler.handleResultSetNext(result);
				} catch (SQLException e) {
					throw new IllegalStateException(e);
				}
				return null;
			}
		};
		var views = reader.read();
		assertEquals(1, queries[0]);
		assertEquals(3, views.size());
		for (var object : views) {
			View view = assertInstanceOf(View.class, object);
			String algorithm = view.getName().substring(2);
			assertEquals(hasAlgorithm ? algorithm : null, view.getSpecifics().get(MySqlCreateViewFactory.ALGORITHM));
			SqlFactory<View> create = dialect.createSqlFactoryRegistry().getSqlFactory(view, SqlType.CREATE);
			String sql = create.createSql(view).get(0).getSqlText();
			assertEquals(hasAlgorithm, sql.contains("ALGORITHM = " + algorithm), sql);
			assertTrue(sql.contains("SELECT 1"), sql);
		}
	}
}
