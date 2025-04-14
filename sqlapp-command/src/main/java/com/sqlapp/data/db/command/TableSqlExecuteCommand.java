/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command;

import java.util.Comparator;
import java.util.List;

import com.sqlapp.data.db.command.properties.TableOptionProperty;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.function.TablePredicate;
import com.sqlapp.jdbc.sql.JdbcHandler;
import com.sqlapp.jdbc.sql.SqlConverter;
import com.sqlapp.jdbc.sql.node.SqlNode;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TableSqlExecuteCommand extends AbstractTableCommand implements TableOptionProperty {

	/** SQL Type */
	private SqlType[] sqlType = null;

	private SqlConverter sqlConverter = new SqlConverter();

	private TablePredicate commitPerTable = (table) -> true;

	private IterationMethod iterationMethod = IterationMethod.TABLE;

	public TableSqlExecuteCommand() {
	}

	@Override
	protected void doRun() {
		execute(getDataSource(), connection -> {
			final Dialect dialect = this.getDialect(connection);
			final List<Table> tables = getTables(connection, dialect);
			final SqlFactoryRegistry sqlFactoryRegistry = dialect.createSqlFactoryRegistry();
			sqlFactoryRegistry.getOption().setTableOptions(this.getTableOptions());
			connection.setAutoCommit(false);
			if (getIterationMethod().isTable()) {
				for (final Table table : tables) {
					for (final SqlType sqlType : this.sqlType) {
						final List<SqlOperation> sqlOperations = sqlFactoryRegistry.createSql(table, sqlType);
						final ParametersContext context = new ParametersContext();
						context.putAll(this.getContext());
						for (final SqlOperation operation : sqlOperations) {
							final SqlNode sqlNode = sqlConverter.parseSql(context, operation.getSqlText());
							final JdbcHandler jdbcHandler = new JdbcHandler(sqlNode);
							jdbcHandler.execute(connection, context);
							commit(connection);
						}
						if (!this.getTableOptions().getCommitPerSqlType().test(sqlType)) {
							commit(connection);
						}
					}
					if (!this.getTableOptions().getCommitPerTable().test(table)) {
						commit(connection);
					}
				}
			} else {
				for (final SqlType sqlType : this.sqlType) {
					final Comparator<Table> comp = sqlType.getTableComparator();
					if (comp != null) {
						tables.sort(comp);
					}
					for (final Table table : tables) {
						final List<SqlOperation> sqlOperations = sqlFactoryRegistry.createSql(table, sqlType);
						final ParametersContext context = new ParametersContext();
						context.putAll(this.getContext());
						for (final SqlOperation operation : sqlOperations) {
							final SqlNode sqlNode = sqlConverter.parseSql(context, operation.getSqlText());
							final JdbcHandler jdbcHandler = new JdbcHandler(sqlNode);
							jdbcHandler.execute(connection, context);
							commit(connection);
						}
						if (!this.getTableOptions().getCommitPerTable().test(table)) {
							commit(connection);
						}
					}
					if (!this.getTableOptions().getCommitPerSqlType().test(sqlType)) {
						commit(connection);
					}
				}
			}
		});
	}

	public static enum IterationMethod {
		TABLE() {
			@Override
			public boolean isTable() {
				return true;
			}
		},
		SQL_TYPE;

		public boolean isTable() {
			return false;
		}
	}

	/**
	 * @param sqlType the sqlType to set
	 */
	public void setSqlType(final SqlType... sqlType) {
		this.sqlType = sqlType;
	}

	public void setCommitPerTable(final boolean bool) {
		this.commitPerTable = table -> bool;
	}
}
