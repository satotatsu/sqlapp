/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.jdbc.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.AbstractIterator;

public class JdbcBatchUpdateHandler extends JdbcHandler {

	private int batchSize = 50;

	public JdbcBatchUpdateHandler(SqlNode node) {
		super(node);
	}

	public JdbcBatchUpdateHandler(SqlNode node,
			GeneratedKeyHandler generatedKeyHandler) {
		super(node, generatedKeyHandler);
	}

	@Override
	protected void doExecute(final Connection connection, final Object context)
			throws SQLException {
		try {
			if (batchSize > 1) {
				doExecuteBatch(connection, context);
			} else {
				doExecuteContext(connection, context);
			}
		} catch (Exception e) {
			if (e instanceof SQLException) {
				throw (SQLException) e;
			}
			throw new RuntimeException(e);
		}
	}

	/**
	 * @return the batchSize
	 */
	public int getBatchSize() {
		return batchSize;
	}

	/**
	 * @param batchSize
	 *            the batchSize to set
	 */
	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

	protected void doExecuteContext(final Connection connection, Object context)
			throws Exception {
		AbstractIterator<Object> itr = new AbstractIterator<Object>() {
			SqlParameterCollection sqlParameters = null;
			PreparedStatement statement = null;
			String sql = null;

			@Override
			protected void handle(Object obj, int index) throws SQLException {
				try {
					sqlParameters = getNode().eval(obj);
					String currentSql = sqlParameters.getSql();
					if (!currentSql.equals(sql)) {
						close(statement, sqlParameters);
					}
					if (statement == null) {
						statement = getStatement(connection, sqlParameters, null);
					} else {
						statement.clearParameters();
					}
					setBind(statement, sqlParameters);
					handlePreparedStatement(statement);
					sql = currentSql;
				} finally {
					close(this.statement, sqlParameters);
					this.statement = null;
				}
			}

			@Override
			protected void callStepLast(int length) throws Exception {
				close(statement, sqlParameters);
			}
		};
		itr.execute(context);
	}

	protected void doExecuteBatch(final Connection connection, Object context)
			throws Exception {
		AbstractIterator<Object> itr = new AbstractIterator<Object>(
				this.batchSize) {
			PreparedStatement statement = null;
			SqlParameterCollection sqlParameters = null;

			@Override
			protected void handle(Object obj, int index) throws SQLException {
				if (index == 0) {
					sqlParameters = getNode().eval(obj);
					statement = getStatement(connection, sqlParameters, null);
					setBind(statement, sqlParameters);
				} else {
					getNode().reEval(obj, sqlParameters);
					setBind(statement, sqlParameters);
				}
				statement.addBatch();
			}

			@Override
			protected void stepHandle(int index, int stepSize)
					throws SQLException {
				int[] ret = statement.executeBatch();
				handleGeneratedKeys(statement);
				statement.clearBatch();
				boolean hasResult=true;
				for (int i = 0; i < ret.length; i++) {
					if (ret[i]==Statement.SUCCESS_NO_INFO){
						hasResult=false;
						break;
					}
				}
				if (hasResult){
					for (int i = 0; i < ret.length; i++) {
						if (ret[i]==Statement.EXECUTE_FAILED){
							handleUpdate(statement, 0);
						} else{
							handleUpdate(statement, ret[i]);
						}
					}
				} else{
					handleUpdate(statement, statement.getLargeUpdateCount());
				}
			}

			@Override
			protected void executeFinally() {
				close(statement, sqlParameters);
			}
		};
		itr.execute(context);
	}
}
