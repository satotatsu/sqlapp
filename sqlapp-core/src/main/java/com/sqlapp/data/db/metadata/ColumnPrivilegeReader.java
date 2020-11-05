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
package com.sqlapp.data.db.metadata;

import java.sql.Connection;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ColumnPrivilege;
import com.sqlapp.data.schemas.SchemaObjectProperties;
/**
 * ColumnPrivilegeのメタデータ
 * @author satoh
 *
 */
public abstract class ColumnPrivilegeReader extends AbstractCatalogObjectMetadataReader<ColumnPrivilege>{
	/**
	 * テーブル名
	 */
	private String tableName=null;
	/**
	 * カラム名
	 */
	private String columnName=null;
	/**
	 * スキーマ名
	 */
	private String schemaName=null;

	public String getColumnName() {
		return columnName;
	}

	public void setColumnName(String columnName) {
		this.columnName = columnName;
	}

	public String getTableName() {
		return tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	/**
	 * @return the schemaName
	 */
	public String getSchemaName() {
		return schemaName;
	}

	/**
	 * @param schemaName the schemaName to set
	 */
	public void setSchemaName(String schemaName) {
		this.schemaName = schemaName;
	}

	protected ColumnPrivilegeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SchemaObjectProperties getSchemaObjectProperties(){
		return SchemaObjectProperties.COLUMN_PRIVILEGES;
	}
	
	/**
	 * カタログ名、スキーマ名、テーブル名、カラム名を含むパラメタコンテキストを作成します。
	 */
	@Override
	protected ParametersContext defaultParametersContext(Connection connection){
		ParametersContext context=newParametersContext(connection, this.getCatalogName());
		context.put(getNameLabel(), nativeCaseString(connection,  this.getTableName()));
		context.put(getColumnNameLabel(), nativeCaseString(connection,  this.getColumnName()));
		return context;
	}

	protected String getNameLabel(){
		return "tableName";
	}

	protected String getColumnNameLabel(){
		return "columnName";
	}

	protected String getTableName(ParametersContext context){
		return (String)context.get(getNameLabel());
	}

	protected String getColumnName(ParametersContext context){
		return (String)context.get(getColumnNameLabel());
	}

}
