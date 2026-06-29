/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.sqlapp.jdbc.sql.node.SqlNode;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StatementHolder implements Closeable {
	private final SqlNode sqlNode;
	private PreparedStatement statement;
	private SqlParameterCollection sqlParameters;
	private BatchExecResult batchExecResult;

	public void setSqlParameters(SqlParameterCollection sqlParameters) {
		this.sqlParameters = sqlParameters;
	}

	public StatementHolder(SqlNode sqlNode) {
		this.sqlNode = sqlNode;
	}

	public void close() {
		try {
			if (statement != null) {
				statement.close();
			}
		} catch (SQLException e) {
		}
	}
}
