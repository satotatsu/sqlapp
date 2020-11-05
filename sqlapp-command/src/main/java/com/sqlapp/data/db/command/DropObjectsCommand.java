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
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.command.AbstractSchemaDataSourceCommand;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.metadata.ObjectNameReaderPredicate;
import com.sqlapp.data.db.metadata.ReadDbObjectPredicate;
import com.sqlapp.data.db.metadata.SchemaReader;
import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.db.sql.ConnectionSqlExecutor;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.AbstractSchemaObjectCollection;
import com.sqlapp.data.schemas.DbObject;
import com.sqlapp.data.schemas.DbObjectCollection;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/**
 * DROPコマンド
 * 
 * @author tatsuo satoh
 * 
 */
public class DropObjectsCommand extends AbstractSchemaDataSourceCommand {
	/**
	 * ダンプに含めるスキーマ
	 */
	private String[] includeSchemas = null;
	/**
	 * ダンプから除くスキーマ
	 */
	private String[] excludeSchemas = null;
	/**
	 * ダンプに含めるテーブル
	 */
	private String[] includeObjects = null;
	/**
	 * ダンプから除くテーブル
	 */
	private String[] excludeObjects = null;
	/**
	 * 現在のカタログのみを対象とするフラグ
	 */
	private boolean onlyCurrentCatalog = true;
	/**
	 * 現在のスキーマのみを対象とするフラグ
	 */
	private boolean onlyCurrentSchema = false;
	/**
	 * オブジェクトのDROPを実施
	 */
	private boolean dropObjects=false;
	/**
	 * テーブルのDROPを実施
	 */
	private boolean dropTables=false;

	private String preDropTableSql;

	private String afterDropTableSql;
	
	protected SchemaReader getSchemaReader(Dialect dialect) throws SQLException{
		CatalogReader catalogReader=dialect.getCatalogReader();
		try(Connection connection=this.getConnection()){
			SchemaReader schemaReader=catalogReader.getSchemaReader();
			if (this.isOnlyCurrentCatalog()) {
				String catalogName = getCurrentCatalogName(connection);
				schemaReader.setCatalogName(catalogName);
			}
			if (this.isOnlyCurrentSchema()) {
				String schemaName = getCurrentSchemaName(connection);
				schemaReader.setSchemaName(schemaName);
			}
			schemaReader.setReadDbObjectPredicate(getMetadataReaderFilter());
			return schemaReader;
		}
	}

	protected ReadDbObjectPredicate getMetadataReaderFilter() {
		ReadDbObjectPredicate readerFilter = new ObjectNameReaderPredicate(
				this.getIncludeSchemas(), this.getExcludeSchemas(),
				this.getIncludeObjects(), this.getExcludeObjects());
		return readerFilter;
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.command.AbstractCommand#doRun()
	 */
	@Override
	protected void doRun() {
		Dialect dialect=this.getDialect();
		SchemaReader schemaReader=null;
		try {
			schemaReader = getSchemaReader(dialect);
		} catch (SQLException e) {
			this.getExceptionHandler().handle(e);
		}
		SqlFactoryRegistry sqlFactoryRegistry=dialect.getSqlFactoryRegistry();
		try(Connection connection=this.getConnection()){
			List<Schema> schemas=schemaReader.getAll(connection);
			for(Schema schema:schemas){
				if (this.isDropObjects()){
					schemaReader.load(connection, schema);
					dropObjects(connection, schemaReader, schema, sqlFactoryRegistry);
				}
				if (this.isDropTables()){
					if (!this.isDropObjects()){
						TableReader tableReader=schemaReader.getTableReader();
						tableReader.setCatalogName(schema.getCatalogName());
						tableReader.setSchemaName(schema.getName());
						tableReader.loadFull(connection, schema);
					}
					dropTables(connection, schemaReader, schema, sqlFactoryRegistry);
				}
			}
		} catch (SQLException e) {
			this.getExceptionHandler().handle(e);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void dropObjects(Connection connection, SchemaReader schemaReader, Schema schema, SqlFactoryRegistry sqlFactoryRegistry) throws SQLException{
		loadDetail(connection, schemaReader, schema);
		ConnectionSqlExecutor operationExecutor=new ConnectionSqlExecutor(connection);
		operationExecutor.setAutoClose(false);
		for(Map.Entry<String, AbstractSchemaObjectCollection> entry:schema.getChildObjectCollectionMap().entrySet()){
			if (SchemaObjectProperties.TABLES.getLabel().equals(entry.getKey())){
				continue;
			}
			DbObjectCollection<DbObject<?>> collection=entry.getValue();
			for(DbObject<?> object:collection){
				SqlFactory<DbObject<?>> sqlFactory=sqlFactoryRegistry.getSqlFactory(object, SqlType.DROP);
				List<SqlOperation> operations=sqlFactory.createSql(object);
				operationExecutor.execute(operations);
			}
		}
	}
	
	protected void loadDetail(Connection connection, SchemaReader schemaReader, Schema schema) throws SQLException{
		schemaReader.load(connection, schema);
	}

	protected void dropTables(Connection connection, SchemaReader schemaReader, Schema schema, SqlFactoryRegistry sqlFactoryRegistry) throws SQLException{
		ConnectionSqlExecutor sqlExecutor=new ConnectionSqlExecutor(connection);
		sqlExecutor.setAutoClose(false);
		if (!CommonUtils.isEmpty(this.getPreDropTableSql())){
			try(Statement statement=connection.createStatement()){
				statement.executeQuery(this.getPreDropTableSql());
			}
		}
		SqlFactory<Table> sqlFactory=sqlFactoryRegistry.getSqlFactory(new Table(), SqlType.DROP);
		List<SqlOperation> operations=sqlFactory.createSql(schema.getTables());
		sqlExecutor.execute(operations);
		if (!CommonUtils.isEmpty(this.getAfterDropTableSql())){
			try(Statement statement=connection.createStatement()){
				statement.executeQuery(this.getAfterDropTableSql());
			}
		}
	}
	
	/**
	 * @return the includeSchemas
	 */
	public String[] getIncludeSchemas() {
		return includeSchemas;
	}

	/**
	 * @param includeSchemas
	 *            the includeSchemas to set
	 */
	public void setIncludeSchemas(String... includeSchemas) {
		this.includeSchemas = includeSchemas;
	}

	/**
	 * @return the excludeSchemas
	 */
	public String[] getExcludeSchemas() {
		return excludeSchemas;
	}

	/**
	 * @param excludeSchemas
	 *            the excludeSchemas to set
	 */
	public void setExcludeSchemas(String... excludeSchemas) {
		this.excludeSchemas = excludeSchemas;
	}



	/**
	 * @return the includeObjects
	 */
	public String[] getIncludeObjects() {
		return includeObjects;
	}

	/**
	 * @param includeObjects the includeObjects to set
	 */
	public void setIncludeObjects(String... includeObjects) {
		this.includeObjects = includeObjects;
	}

	/**
	 * @return the excludeObjects
	 */
	public String[] getExcludeObjects() {
		return excludeObjects;
	}

	/**
	 * @param excludeObjects the excludeObjects to set
	 */
	public void setExcludeObjects(String... excludeObjects) {
		this.excludeObjects = excludeObjects;
	}

	/**
	 * @return the onlyCurrentCatalog
	 */
	public boolean isOnlyCurrentCatalog() {
		return onlyCurrentCatalog;
	}

	/**
	 * @param onlyCurrentCatalog
	 *            the onlyCurrentCatalog to set
	 */
	public void setOnlyCurrentCatalog(boolean onlyCurrentCatalog) {
		this.onlyCurrentCatalog = onlyCurrentCatalog;
	}

	/**
	 * @return the onlyCurrentSchema
	 */
	public boolean isOnlyCurrentSchema() {
		return onlyCurrentSchema;
	}

	/**
	 * @param onlyCurrentSchema
	 *            the onlyCurrentSchema to set
	 */
	public void setOnlyCurrentSchema(boolean onlyCurrentSchema) {
		this.onlyCurrentSchema = onlyCurrentSchema;
	}

	/**
	 * @return the dropObjects
	 */
	public boolean isDropObjects() {
		return dropObjects;
	}

	/**
	 * @param dropObjects the dropObjects to set
	 */
	public void setDropObjects(boolean dropObjects) {
		this.dropObjects = dropObjects;
	}

	/**
	 * @return the dropTables
	 */
	public boolean isDropTables() {
		return dropTables;
	}

	/**
	 * @param dropTables the dropTables to set
	 */
	public void setDropTables(boolean dropTables) {
		this.dropTables = dropTables;
	}

	/**
	 * @return the preDropTableSql
	 */
	public String getPreDropTableSql() {
		return preDropTableSql;
	}

	/**
	 * @param preDropTableSql the preDropTableSql to set
	 */
	public void setPreDropTableSql(String preDropTableSql) {
		this.preDropTableSql = preDropTableSql;
	}

	/**
	 * @return the afterDropTableSql
	 */
	public String getAfterDropTableSql() {
		return afterDropTableSql;
	}

	/**
	 * @param afterDropTableSql the afterDropTableSql to set
	 */
	public void setAfterDropTableSql(String afterDropTableSql) {
		this.afterDropTableSql = afterDropTableSql;
	}





}
