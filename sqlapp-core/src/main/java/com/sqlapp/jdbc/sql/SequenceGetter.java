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

import java.io.Closeable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.List;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.FileUtils;

public class SequenceGetter implements Closeable {

	private final Connection connection;
	private final Dialect dialect;
	private final Sequence sequence;
	private final int cacheSize;
	private boolean initialized = false;
	private SqlParameterCollection sqlParameters;
	private PreparedStatement statement = null;
	private final Converters converters = Converters.getDefault();
	private ArrayDeque<Object> deque;

	public SequenceGetter(Connection connection, Sequence sequence, int cacheSize) {
		this.connection = connection;
		this.dialect = DialectResolver.getInstance().getDialect(connection);
		this.sequence = sequence;
		this.cacheSize = cacheSize;
	}

	public SequenceGetter(Connection connection, Dialect dialect, Sequence sequence, int cacheSize) {
		this.connection = connection;
		this.dialect = dialect;
		this.sequence = sequence;
		this.cacheSize = cacheSize;
	}

	private void initialize() throws SQLException {
		if (initialized) {
			return;
		}
		SqlFactoryRegistry registory = dialect.createSqlFactoryRegistry();
		List<SqlNode> list = registory.createSqlNodes(sequence, SqlType.SEQUENCE_NEXT_VALUES);
		if (list.isEmpty()) {
			throw new UnsupportedOperationException(dialect.getClass().getName() + " does not support sequence.");
		}
		SqlNode sqlNode = list.get(0);
		sqlParameters = sqlNode.eval(cacheSize);
		statement = sqlParameters.createStatement(connection);
		deque = new ArrayDeque<>(cacheSize);
		initialized = true;
	}

	public <T> T get(Class<T> clazz) throws SQLException {
		initialize();
		if (deque.isEmpty()) {
			loadSequences();
		}
		return converters.convertObject(deque.removeFirst(), clazz);
	}

	private <T> void loadSequences() throws SQLException {
		initialize();
		sqlParameters.setBind(statement);
		try (ResultSet resultSet = statement.executeQuery()) {
			while (resultSet.next()) {
				final Object val = resultSet.getObject(1);
				deque.add(val);
			}
		}
	}

	@Override
	public void close() {
		FileUtils.close(statement);
	}

}
