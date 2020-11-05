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
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.AbstractSchemaObjectCollection;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.SchemaProperties;
/**
 * スキーマに含まれるオブジェクト読み込み抽象クラス
 * @author satoh
 *
 * @param <T>
 */
public abstract class AbstractSchemaObjectReader<T extends AbstractSchemaObject<? super T>> extends AbstractNamedMetadataReader<T, Schema>{

	protected AbstractSchemaObjectReader(Dialect dialect) {
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
	 * @param objectName the objectName to set
	 */
	@Override
	public void setObjectName(String objectName) {
		if (objectName==null){
			super.setObjectName(objectName);
			return;
		}
		String[] splits=objectName.split("\\.");
		int i=0;
		super.setObjectName(splits[i++]);
		if (splits.length>1){
			this.setSchemaName(splits[i++]);
		}
		if (splits.length>2){
			this.setCatalogName(splits[i++]);
		}
	}
	
	@Override
	public void loadFull(Connection connection, Schema schema) {
		List<T> list=getAllFull(connection);
		int size=list.size();
		AbstractSchemaObjectCollection<T> c=getSchemaObjectList(schema);
		for(int i=0;i<size;i++){
			T obj=list.get(i);
			c.add(obj);
		}
	}

	@Override
	public void load(Connection connection, Schema schema) {
		List<T> list=getAll(connection);
		int size=list.size();
		AbstractSchemaObjectCollection<T> c=getSchemaObjectList(schema);
		for(int i=0;i<size;i++){
			T obj=list.get(i);
			c.add(obj);
		}
	}

	@SuppressWarnings("unchecked")
	private AbstractSchemaObjectCollection<T> getSchemaObjectList(Schema target){
		return (AbstractSchemaObjectCollection<T>)getSchemaObjectProperties().getValue(target);
	}

	protected abstract SchemaObjectProperties getSchemaObjectProperties();

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.dialect.metadata.AbstractDBMetadataFactory#defaultParametersContext(java.sql.Connection)
	 */
	@Override
	protected ParametersContext defaultParametersContext(Connection connection){
		ParametersContext context=super.defaultParametersContext(connection);
		context.put(SchemaProperties.SCHEMA_NAME.getLabel(), nativeCaseString(connection,  this.getSchemaName()));
		context.put(getNameLabel(), nativeCaseString(connection,  this.getObjectName()));
		return context;
	}
	
	
	/**
	 * カタログ名を含むパラメタコンテキストを作成します。
	 * @param obj 
	 */
	@Override
	protected ParametersContext toParametersContext(T obj){
		ParametersContext context=new ParametersContext();
		context.put(SchemaProperties.CATALOG_NAME.getLabel(), obj.getCatalogName());
		context.put(SchemaProperties.SCHEMA_NAME.getLabel(), obj.getSchemaName());
		context.put(this.getNameLabel(), obj.getName());
		return context;
	}
}
