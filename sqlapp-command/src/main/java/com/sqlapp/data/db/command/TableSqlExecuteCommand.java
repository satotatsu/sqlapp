/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-command.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.command;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.metadata.SchemaReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.function.TablePredicate;
import com.sqlapp.jdbc.sql.JdbcHandler;
import com.sqlapp.jdbc.sql.SqlConverter;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;

public class TableSqlExecuteCommand extends AbstractSchemaDataSourceCommand{
	
	/**SQL Type*/
	private SqlType[] sqlType=null;

	private String catalogName;

	private String schemaName;
	
	private TableOptions tableOptions=new TableOptions();

	private SqlConverter sqlConverter=new SqlConverter();

	private TablePredicate targetTable=table->false;

	private TablePredicate commitPerTable=(table)->true;
	
	public TableSqlExecuteCommand(){
	}
	
	@Override
	protected void doRun() {
		final Dialect dialect=this.getDialect();
		SchemaReader schemaReader=null;
		try {
			schemaReader = getSchemaReader(dialect);
		} catch (final SQLException e) {
			this.getExceptionHandler().handle(e);
		}
		final Map<String, Schema> schemaMap=this.getSchemas(dialect, schemaReader);
		final Catalog catalog=new Catalog();
		catalog.setDialect(dialect);
		schemaMap.forEach((k,v)->{
			catalog.getSchemas().add(v);
		});
		final SqlFactoryRegistry sqlFactoryRegistry=dialect.getSqlFactoryRegistry();
		sqlFactoryRegistry.getOption().setTableOptions(tableOptions);
		Connection connection=null;
		try{
			connection=this.getConnection();
			connection.setAutoCommit(false);
			final List<Table> tables=CommonUtils.list();
			for(final Schema schema:catalog.getSchemas()) {
				for(final Table table:schema.getTables()) {
					tables.add(table);
				}
			}
			for(final SqlType sqlType:this.sqlType) {
				final Comparator<Table> comp=sqlType.getTableComparator();
				if (comp!=null) {
					tables.sort(comp);
				}
				for(final Table table:tables) {
					if (!targetTable.test(table)) {
						continue;
					}
					final List<SqlOperation> sqlOperations=sqlFactoryRegistry.createSql(table, sqlType);
					final ParametersContext context=new ParametersContext();
					context.putAll(this.getContext());
					for(final SqlOperation operation:sqlOperations){
						final SqlNode sqlNode=sqlConverter.parseSql(context, operation.getSqlText());
						final JdbcHandler jdbcHandler=new JdbcHandler(sqlNode);
						jdbcHandler.execute(connection, context);
						connection.commit();
					}
					if (!this.getTableOptions().getCommitPerTable().test(table)){
						connection.commit();
					}
				}
			}
			connection.commit();
		} catch (final RuntimeException e) {
			rollback(connection);
			this.getExceptionHandler().handle(e);
		} catch (final SQLException e) {
			rollback(connection);
			this.getExceptionHandler().handle(e);
		}
	}
	
	protected SchemaReader getSchemaReader(final Dialect dialect) throws SQLException{
		try(Connection connection=this.getConnection()){
			return getSchemaReader(connection, dialect);
		}
	}

	protected SchemaReader getSchemaReader(final Connection connection, final Dialect dialect) throws SQLException{
		final CatalogReader catalogReader=dialect.getCatalogReader();
		final SchemaReader schemaReader=catalogReader.getSchemaReader();
		if (!CommonUtils.isEmpty(getCatalogName())) {
			schemaReader.setCatalogName(getCatalogName());
		} else {
			final String catalogName = getCurrentCatalogName(connection);
			schemaReader.setCatalogName(catalogName);
		}
		if (!CommonUtils.isEmpty(getSchemaName())) {
			schemaReader.setSchemaName(getSchemaName());
		} else {
			final String schemaName = getCurrentSchemaName(connection);
			schemaReader.setSchemaName(schemaName);
		}
		return schemaReader;
	}
	
	private void rollback(final Connection connection){
		if (connection==null){
			return;
		}
		try {
			connection.rollback();
		} catch (final SQLException e) {
		}
	}

	public String getCatalogName() {
		return catalogName;
	}

	public void setCatalogName(final String catalogName) {
		this.catalogName = catalogName;
	}

	public String getSchemaName() {
		return schemaName;
	}

	public void setSchemaName(final String schemaName) {
		this.schemaName = schemaName;
	}

	public TableOptions getTableOptions() {
		return tableOptions;
	}

	public Predicate<Table> getTargetTable() {
		return targetTable;
	}

	public void setTargetTable(final TablePredicate targetTable) {
		this.targetTable = targetTable;
	}

	public void setTableOptions(final TableOptions tableOptions) {
		this.tableOptions = tableOptions;
	}

	public void setSqlConverter(final SqlConverter sqlConverter) {
		this.sqlConverter = sqlConverter;
	}

	/**
	 * @return the sqlType
	 */
	public SqlType[] getSqlType() {
		return sqlType;
	}

	/**
	 * @param sqlType the sqlType to set
	 */
	public void setSqlType(final SqlType... sqlType) {
		this.sqlType = sqlType;
	}

	public TablePredicate getCommitPerTable() {
		return commitPerTable;
	}

	public void setCommitPerTable(final TablePredicate commitPerTable) {
		this.commitPerTable = commitPerTable;
	}

	public void setCommitPerTable(final boolean bool) {
		this.commitPerTable = table->bool;
	}

	public SqlConverter getSqlConverter() {
		return sqlConverter;
	}
}
