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
import java.util.Collections;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;

public class SequenceGenerator implements Closeable {

	private final Connection connection;
	private final Dialect dialect;
	private final Sequence sequence;
	private boolean initialized = false;
	private SqlParameterCollection sqlParameters;
	private PreparedStatement statement = null;

	public SequenceGenerator(Connection connection, Sequence sequence) {
		this.connection = connection;
		dialect = DialectResolver.getInstance().getDialect(connection);
		this.sequence = sequence;
	}

	public SequenceGenerator(Connection connection, Dialect dialect, Sequence sequence) {
		this.connection = connection;
		this.dialect = dialect;
		this.sequence = sequence;
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
		sqlParameters = sqlNode.eval(1);
		statement = sqlParameters.createStatement(connection);
		initialized = true;
	}

	public List<Object> get(int size) throws SQLException {
		if (size == 0) {
			return Collections.emptyList();
		}
		initialize();
		final List<Object> list = CommonUtils.list(size);
		statement.setInt(1, size);
		try (ResultSet resultSet = statement.executeQuery()) {
			while (resultSet.next()) {
				final Object val = resultSet.getObject(1);
				list.add(val);
			}
		}
		return list;
	}

	@Override
	public void close() {
		FileUtils.close(statement);
		sqlParameters = null;
		statement = null;
		this.initialized = false;
	}

}
