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

package com.sqlapp.data.db.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.MviewLog;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.ReferenceColumnCollection;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.util.TripleKeyMap;

public abstract class MviewLogColumnReader extends  AbstractNamedMetadataReader<ReferenceColumn, MviewLog>{

	protected MviewLogColumnReader(Dialect dialect) {
		super(dialect);
	}
	/**
	 * スキーマ名
	 */
	private String schemaName=null;
	public String getSchemaName() {
		return schemaName;
	}

	public void setSchemaName(String schemaName) {
		this.schemaName = schemaName;
	}

	/**
	 * コンテキストからテーブル名の取得を行います
	 * @param context
	 */
	protected String getTableName(ParametersContext context){
		return (String)context.get(getNameLabel());
	}
	
	/**
	 * コンテキストへテーブル名の設定を行います
	 * @param context
	 * @param tableName
	 */
	protected void setTableName(ParametersContext context, String tableName){
		context.put(getNameLabel(), tableName);
	}
	
	/**
	 * カラム名
	 */
	private String columnName=null;

	@Override
	public void loadFull(Connection connection, MviewLog table) {
		List<ReferenceColumn> list=getAllFull(connection);
		int size=list.size();
		List<ReferenceColumn> c=getSchemaObjectList(table);
		for(int i=0;i<size;i++){
			ReferenceColumn obj=list.get(i);
			c.add(obj);
		}
	}

	@Override
	public void load(Connection connection, MviewLog table) {
		List<ReferenceColumn> list=getAll(connection);
		int size=list.size();
		List<ReferenceColumn> c=getSchemaObjectList(table);
		for(int i=0;i<size;i++){
			ReferenceColumn obj=list.get(i);
			c.add(obj);
		}
	}

	/**
	 * リストからTripleKeyMapに変換します
	 * @param list
	 */
	protected TripleKeyMap<String, String, String, List<ReferenceColumn>> toKeyMap(List<ReferenceColumn> list){
		TripleKeyMap<String, String, String, List<ReferenceColumn>> map=new TripleKeyMap<String, String, String, List<ReferenceColumn>>();
		for(ReferenceColumn obj:list){
			List<ReferenceColumn> tableConsts=map.get(obj.getCatalogName(), obj.getSchemaName(), obj.getTableName());
			if (tableConsts==null){
				tableConsts=list();
				map.put(obj.getCatalogName(), obj.getSchemaName(), obj.getTableName(), tableConsts);
			}
			tableConsts.add(obj);
		}
		return map;
	}

	protected ReferenceColumnCollection getSchemaObjectList(MviewLog mviewLog) {
		return mviewLog.getColumns();
	}

	/**
	 * カタログ名、スキーマ名、テーブル名、カラム名を含むパラメタコンテキストを作成します。
	 */
	@Override
	protected ParametersContext defaultParametersContext(Connection connection){
		ParametersContext context=newParametersContext(connection, this.getCatalogName(), this.getSchemaName());
		this.setTableName(context, nativeCaseString(connection,  this.getObjectName()));
		context.put(getNameLabel(), nativeCaseString(connection,  this.getColumnName()));
		return context;
	}

	@Override
	protected String getNameLabel(){
		return SchemaProperties.COLUMN_NAME.getLabel();
	}

	public String getColumnName() {
		return columnName;
	}

	public void setColumnName(String columnName) {
		this.columnName = columnName;
	}

	protected String getColumnName(ParametersContext context){
		return (String)context.get(SchemaProperties.COLUMN_NAME.getLabel());
	}
}
