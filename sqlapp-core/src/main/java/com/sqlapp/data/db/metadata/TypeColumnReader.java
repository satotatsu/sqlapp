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

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.DbObjects;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.Type;
import com.sqlapp.data.schemas.TypeColumn;
import com.sqlapp.data.schemas.TypeColumnCollection;
import com.sqlapp.util.TripleKeyMap;

public abstract class TypeColumnReader extends AbstractNamedMetadataReader<TypeColumn, Type>{

	protected final String TYPE_NAME="type_name";
	
	protected TypeColumnReader(Dialect dialect) {
		super(dialect);
	}

	/**
	 * スキーマ名
	 */
	private String schemaName=null;
	/**
	 * カラム名
	 */
	private String columnName=null;
	
	@Override
	public void loadFull(Connection connection, Type tp) {
		List<TypeColumn> list=getAllFull(connection);
		int size=list.size();
		List<TypeColumn> c=getSchemaObjectList(tp);
		for(int i=0;i<size;i++){
			TypeColumn obj=list.get(i);
			c.add(obj);
		}
	}
	
	@Override
	public void load(Connection connection, Type tp) {
		List<TypeColumn> list=getAll(connection);
		int size=list.size();
		List<TypeColumn> c=getSchemaObjectList(tp);
		for(int i=0;i<size;i++){
			TypeColumn obj=list.get(i);
			c.add(obj);
		}
	}
	
	protected TypeColumnCollection getSchemaObjectList(Type tp) {
		return tp.getColumns();
	}
		
	/**
	 * カタログ名、スキーマ名、タイプ名、タイプカラム名を含むパラメタコンテキストを作成します。
	 */
	@Override
	protected ParametersContext defaultParametersContext(Connection connection){
		ParametersContext context=newParametersContext(connection, this.getCatalogName(), this.getSchemaName());
		context.put(DbObjects.TYPE.getCamelCaseNameLabel(), nativeCaseString(connection,  this.getObjectName()));
		context.put(getNameLabel(), nativeCaseString(connection,  this.getColumnName()));
		return context;
	}

	
	/**
	 * リストからTripleKeyMapに変換します
	 * @param list
	 */
	protected TripleKeyMap<String, String, String, List<TypeColumn>> toKeyMap(List<TypeColumn> list){
		TripleKeyMap<String, String, String, List<TypeColumn>> map=new TripleKeyMap<String, String, String, List<TypeColumn>>();
		for(TypeColumn obj:list){
			List<TypeColumn> tableConsts=map.get(obj.getCatalogName(), obj.getSchemaName(), obj.getDataTypeName());
			if (tableConsts==null){
				tableConsts=list();
				map.put(obj.getCatalogName(), obj.getSchemaName(), obj.getDataTypeName(), tableConsts);
			}
			tableConsts.add(obj);
		}
		return map;
	}
	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.dialect.metadata.AbstractNamedMetadataFactory#getNameLabel()
	 */
	@Override
	protected String getNameLabel(){
		return SchemaProperties.COLUMN_NAME.getLabel();
	}

	public String getSchemaName() {
		return schemaName;
	}

	public void setSchemaName(String schemaName) {
		this.schemaName = schemaName;
	}


	public String getColumnName() {
		return columnName;
	}

	public void setColumnName(String columnName) {
		this.columnName = columnName;
	}
	
	protected TypeColumn createObject(){
		TypeColumn obj=new TypeColumn();
		obj.setDialect(this.getDialect());
		return obj;
	}

	protected TypeColumn createObject(String name){
		TypeColumn obj=new TypeColumn(name);
		obj.setDialect(this.getDialect());
		return obj;
	}

}
