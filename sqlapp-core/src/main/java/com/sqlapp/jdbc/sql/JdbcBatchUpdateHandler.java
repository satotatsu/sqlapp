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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.node.BindVariableNode;
import com.sqlapp.jdbc.sql.node.Node;
import com.sqlapp.jdbc.sql.node.ParameterMarkerNode;
import com.sqlapp.jdbc.sql.node.SqlPartNode;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.AbstractIterator;
import com.sqlapp.util.FileUtils;

public class JdbcBatchUpdateHandler extends JdbcHandler {

	private int batchSize = 50;

	private boolean evaluateSqlPerRow;

	/**
	 * Enables evaluation of dynamic SQL for every row, flushing the current batch
	 * whenever the SQL changes. The default is false: only fixed SQL templates
	 * are accepted and their bind values are refreshed with {@code reEval()}.
	 */
	public void setEvaluateSqlPerRow(boolean evaluateSqlPerRow) {
		this.evaluateSqlPerRow = evaluateSqlPerRow;
	}

	public boolean isEvaluateSqlPerRow() {
		return evaluateSqlPerRow;
	}

	public JdbcBatchUpdateHandler(SqlNode node) {
		super(node);
	}

	public JdbcBatchUpdateHandler(SqlNode node, GeneratedKeyHandler generatedKeyHandler) {
		super(node, generatedKeyHandler);
	}

	@Override
	protected void doExecute(final Connection connection, final Object context) throws SQLException {
		try {
			if (batchSize > 1) {
				doExecuteBatch(connection, context);
			} else {
				doExecuteContext(connection, context);
			}
		} catch (RuntimeException e) {
			throw e;
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
	 * @param batchSize the batchSize to set
	 */
	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

	protected void doExecuteContext(final Connection connection, Object context) throws Exception {
		AbstractIterator<Object> iterator = new AbstractIterator<Object>() {
			@Override
			protected void handle(Object obj, int index) throws SQLException {
				SqlParameterCollection parameters = getNode().eval(obj);
				PreparedStatement statement = null;
				try {
					statement = getStatement(connection, parameters, null);
					setBind(statement, parameters);
					handlePreparedStatement(statement);
				} finally {
					close(statement, parameters);
				}
			}
		};
		iterator.execute(context);
	}

	protected void doExecuteBatch(final Connection connection, Object context) throws Exception {
		List<BindVariableNode> bindings = new ArrayList<>();
		if (!evaluateSqlPerRow) {
			validateFixedSql(getNode(), bindings);
		}
		class BatchIterator extends AbstractIterator<Object> {
			PreparedStatement statement;
			SqlParameterCollection sqlParameters;
			String sql;
			int pending;
			final List<SqlParameterCollection> evaluated = new ArrayList<>();
			final Set<Closeable> retiredResources = Collections.newSetFromMap(new IdentityHashMap<>());

			void rememberResources() {
				for (BindParameterHolder holder : sqlParameters.getBindParameters()) {
					holder.stream().forEach(parameter -> {
						if (parameter.getValue() instanceof Closeable) {
							retiredResources.add((Closeable) parameter.getValue());
						}
					});
				}
			}

			void closeRetiredResources() {
				if (retiredResources.isEmpty()) {
					return;
				}
				// The current parameter collection still owns its current values.
				for (BindParameterHolder holder : sqlParameters.getBindParameters()) {
					holder.stream().forEach(parameter -> retiredResources.remove(parameter.getValue()));
				}
				retiredResources.forEach(FileUtils::close);
				retiredResources.clear();
			}

			@Override
			protected void handle(Object obj, int index) throws SQLException {
				if (!evaluateSqlPerRow && obj instanceof ParametersContext) {
					ParametersContext parameters = (ParametersContext) obj;
					for (BindVariableNode binding : bindings) {
						if (parameters.getOperator(binding.getBindParameter().getName()) != null) {
							throw new IllegalArgumentException("Dynamic parameter operators require setEvaluateSqlPerRow(true)");
						}
					}
				}
				if (evaluateSqlPerRow || sqlParameters == null) {
					SqlParameterCollection next = getNode().eval(obj);
					evaluated.add(next);
					String nextSql = next.getSql();
					if (statement != null && !nextSql.equals(sql)) {
						// Keep the new row's resources alive while flushing older rows.
						evaluated.remove(evaluated.size() - 1);
						try {
							flush();
						} finally {
							evaluated.add(next);
						}
						close(statement, null);
						statement = null;
					}
					sqlParameters = next;
					sql = nextSql;
				} else {
					rememberResources();
					if (!getNode().reEval(obj, sqlParameters)) {
						throw new IllegalArgumentException("Batch parameter structure changed; use setEvaluateSqlPerRow(true)");
					}
				}
				if (statement == null) {
					statement = getStatement(connection, sqlParameters, null);
				}
				if (evaluateSqlPerRow) {
					statement.clearParameters();
				}
				setBind(statement, sqlParameters);
				statement.addBatch();
				if (++pending == batchSize) {
					flush();
				}
			}

			void flush() throws SQLException {
				if (pending == 0) {
					return;
				}
				int[] ret = statement.executeBatch();
				handleGeneratedKeys(statement);
				statement.clearBatch();
				for (int count : ret) {
					if (count == Statement.EXECUTE_FAILED) {
						throw new SQLException("Batch statement execution failed");
					}
					if (count >= 0) {
						handleUpdate(statement, count);
					}
				}
				pending = 0;
				if (evaluateSqlPerRow) {
					evaluated.forEach(parameters -> close(null, parameters));
					evaluated.clear();
				} else {
					closeRetiredResources();
				}
			}

			void closeResources() {
				close(statement, null);
				closeRetiredResources();
				evaluated.forEach(parameters -> close(null, parameters));
			}
		}
		BatchIterator iterator = new BatchIterator();
		try {
			iterator.execute(context);
			iterator.flush();
		} finally {
			iterator.closeResources();
		}
	}

	private void validateFixedSql(Node node, List<BindVariableNode> bindings) {
		Class<?> type = node.getClass();
		if (type == BindVariableNode.class) {
			bindings.add((BindVariableNode) node);
		} else if (type != SqlNode.class && type != SqlPartNode.class && type != ParameterMarkerNode.class) {
			throw new IllegalArgumentException("Batch SQL contains " + type.getSimpleName()
					+ "; use setEvaluateSqlPerRow(true) for dynamic or custom nodes");
		}
		for (Node child : node.getChildNodes()) {
			validateFixedSql(child, bindings);
		}
	}
}
