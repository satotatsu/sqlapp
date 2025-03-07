/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.jdbc.sql;

import static com.sqlapp.util.CommonUtils.list;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.DataSourceSqlExecutor;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * JdbcHandlerをHSQLのメモリDBでテストします
 * 
 * @author satoh
 * 
 */
public class JdbcHandlerTest extends AbstractDbTest {

	private Table table;

	private final MapSqlRegistry sqlRegistory = new MapSqlRegistry();

	private static final String TABLE_NAME = "TABLEA";

	private static final String INSERT = "INSERT";

	private static final String INSERT_SQL = "insert into " + TABLE_NAME
			+ " (\"id\", \"a\") values (default, /*a*/'a')";

	Dialect dialect = null;

	@BeforeEach
	public void before() {
		dialect = DialectResolver.getInstance().getDialect("hsql", 2, 0);
	}

	@BeforeEach
	public void setUp() throws Exception {
		createDataSource();
		table = new Table(TABLE_NAME);
		Column column = new Column("id");
		column.setDataType(DataType.BIGINT).setIdentity(true)
				.setIdentityStep(1).setIdentityStartValue(0);
		table.getColumns().add(column);
		//
		column = new Column("a");
		column.setDataType(DataType.VARCHAR).setLength(255);
		table.getColumns().add(column);
		//
		sqlRegistory.put(INSERT, INSERT_SQL);
	}

	@Test
	public void test() throws SQLException {
		final Connection connection = getConnection();
		final SqlFactoryRegistry sqlFactoryRegistry = dialect
				.createSqlFactoryRegistry();
		SqlFactory<Table> sqlFactory = sqlFactoryRegistry.getSqlFactory(
				table, State.Added);
		final DataSourceSqlExecutor executer = new DataSourceSqlExecutor(
				dataSource);
		executer.execute(sqlFactory.createSql(table));
		//
		final SqlNode node = sqlRegistory.get(INSERT, null);
		final ParametersContext context = new ParametersContext();
		final JdbcHandler handler = new JdbcHandler(node, new GeneratedKeyHandler() {

			@Override
			public void handle(final long rowNo, final GeneratedKeyInfo generatedKeyInfo) {
				assertEquals(rowNo, generatedKeyInfo.getValue());
			}
		});
		context.put("a", "vala");
		handler.execute(connection, context);
		context.put("a", "valb");
		handler.execute(connection, context);
		//
		final List<ParametersContext> list = list();
		ParametersContext val1=context.clone();
		val1.put("a", "valc");
		list.add(val1);
		val1=context.clone();
		val1.put("a", "vald");
		list.add(val1);
		JdbcBatchUpdateHandler batchUpdateHandler = new JdbcBatchUpdateHandler(
				node);
		batchUpdateHandler.execute(connection, list,
				new GeneratedKeyHandler() {
					int rowCount = 0;

					@Override
					public void handle(final long rowNo,
							final GeneratedKeyInfo generatedKeyInfo) {
						assertEquals((rowCount), rowNo);
						assertEquals(list.size() + rowCount, generatedKeyInfo
								.getValue(Integer.class).intValue());
						rowCount++;
					}
				});
		//
		batchUpdateHandler = new JdbcBatchUpdateHandler(node);
		list.clear();
		val1=context.clone();
		val1.put("a", "vale");
		list.add(val1);
		val1=context.clone();
		val1.put("a", "valf");
		val1=context.clone();
		val1.put("a", "valg");
		list.add(val1);
		batchUpdateHandler.setBatchSize(10);
		batchUpdateHandler.execute(connection, list,
				new GeneratedKeyHandler() {
					long rowNumber = 4;

					@Override
					public void handle(final long rowNo,
							final GeneratedKeyInfo generatedKeyInfo) {
						assertEquals(rowNo + rowNumber,
								generatedKeyInfo.getValue());
					}
				});
		//
		sqlFactory = sqlFactoryRegistry.getSqlFactory(table, SqlType.TRUNCATE);
		executer.execute(sqlFactory.createSql(table));
		//
		sqlFactory = sqlFactoryRegistry.getSqlFactory(table, SqlType.DROP);
		executer.execute(sqlFactory.createSql(table));

	}

}
