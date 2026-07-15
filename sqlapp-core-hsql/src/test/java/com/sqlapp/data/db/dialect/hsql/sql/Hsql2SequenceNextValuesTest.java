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

package com.sqlapp.data.db.dialect.hsql.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.jdbc.DataSourceConnectionUtils;
import com.sqlapp.jdbc.SqlappDataSource;
import com.sqlapp.jdbc.function.SQLConsumer;
import com.sqlapp.jdbc.sql.SequenceGetter;
import com.sqlapp.jdbc.sql.SequenceValueGenerator;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.zaxxer.hikari.HikariDataSource;

/**
 * MySQL用のCreateコマンドテスト
 * 
 * @author tatsuo satoh
 * 
 */
public class Hsql2SequenceNextValuesTest {
	protected DataSource createDataSource() throws SQLException {
		final HikariDataSource dataSource = new HikariDataSource();
		dataSource.setJdbcUrl("jdbc:hsqldb:mem:test;shutdown=true");
		dataSource.setMaximumPoolSize(2);
		dataSource.setMinimumIdle(1);
		// dataSource.setDriverClassName("org.hsqldb.jdbcDriver");
		return new SqlappDataSource(dataSource);
	}

	protected void testDb(SQLConsumer<Connection> cons) throws SQLException {
		DataSource dataSource = createDataSource();
		DataSourceConnectionUtils.executeTranAndCloseDataSource(dataSource, conn -> {
			cons.accept(conn);
		});
	}

	@Test
	public void testSequece() throws SQLException {
		testDb(connection -> {
			Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			SqlFactoryRegistry registory = dialect.createSqlFactoryRegistry();
			Sequence sequence = new Sequence("SEQA");
			sequence.setStartValue(3);
			sequence.setIncrementBy(4);
			List<SqlNode> list = registory.createSqlNodes(sequence, SqlType.CREATE);
			SqlNode createSqlNode = list.get(0);
			SqlParameterCollection sqlParameters = createSqlNode.createSqlParameters();
			try (PreparedStatement statement = sqlParameters.createStatement(connection)) {
				statement.execute();
			}
			list = registory.createSqlNodes(sequence, SqlType.SEQUENCE_NEXT_VALUES);
			SqlNode nextValSqlNode = list.get(0);
			sqlParameters = nextValSqlNode.eval(10);
			int i = 0;
			try (PreparedStatement statement = sqlParameters.createStatement(connection)) {
				sqlParameters.setBind(statement);
				try (ResultSet resultSet = statement.executeQuery()) {
					while (resultSet.next()) {
						assertEquals(3 + 4 * i, resultSet.getInt(1));
						i++;
					}
				}
				assertEquals(10, i);
			}
			try (SequenceGetter sequenceGetter = new SequenceGetter(connection, dialect, sequence, 10)) {
				for (int j = 0; j < 100; j++) {
					int val = sequenceGetter.get(int.class);
					assertEquals(3 + 4 * i, val);
					i++;
				}
			}
			try (SequenceValueGenerator sequenceGenerator = new SequenceValueGenerator(connection, dialect, sequence)) {
				List<Object> seqList = sequenceGenerator.generateValues(100);
				for (int j = 0; j < seqList.size(); j++) {
					int val = Converters.getDefault().convertObject(seqList.get(j), int.class);
					assertEquals(3 + 4 * i, val);
					i++;
				}
			}
		});
	}
}
