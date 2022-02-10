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
import com.sqlapp.data.schemas.AbstractNamedObject;
import com.sqlapp.data.schemas.AbstractNamedObjectCollection;
import com.sqlapp.data.schemas.Catalog;

public abstract class AbstractCatalogNamedObjectMetadataReader<T extends AbstractNamedObject<T>> extends AbstractCatalogObjectMetadataReader<T>{
	/**
	 * オブジェクト名
	 */
	private String objectName=null;

	public String getObjectName() {
		return objectName;
	}

	public void setObjectName(String objectName) {
		this.objectName = objectName;
	}

	protected AbstractCatalogNamedObjectMetadataReader(Dialect dialect) {
		super(dialect);
	}

	/**
	 * 指定したカタログにデータを読み込みを行います
	 */
	@Override
	public void loadFull(Connection connection, Catalog catalog) {
		List<T> list=getAllFull(connection);
		int size=list.size();
		AbstractNamedObjectCollection<T> c=getSchemaObjectList(catalog);
		for(int i=0;i<size;i++){
			T obj=list.get(i);
			c.add(obj);
		}
	}
	
	@SuppressWarnings("unchecked")
	protected AbstractNamedObjectCollection<T> getSchemaObjectList(
			Catalog catalog){
		return (AbstractNamedObjectCollection<T>)getSchemaObjectProperties().getValue(catalog);
	}
	
	protected abstract String getNameLabel();

	protected ParametersContext toParametersContext(T obj){
		ParametersContext context=super.toParametersContext(obj);
		context.put(getNameLabel(), this.getObjectName());
		return context;
	}
	
	protected String getObjectName(ParametersContext context){
		return (String)context.get(getNameLabel());
	}


	/**
	 * カタログ名、スキーマ名、ディレクトリ名を含むパラメタコンテキストを作成します。
	 */
	@Override
	protected ParametersContext defaultParametersContext(Connection connection){
		ParametersContext context=newParametersContext(connection, this.getCatalogName());
		context.put(getNameLabel(), nativeCaseString(connection,  this.getObjectName()));
		return context;
	}


}
