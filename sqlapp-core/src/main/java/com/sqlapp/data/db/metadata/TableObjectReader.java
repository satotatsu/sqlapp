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

import java.util.List;
import java.util.Collection;
import java.lang.reflect.Array;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.TripleKeyMap;
/**
 * テーブル所有オブジェクト共通の抽象クラスです
 * @author satoh
 *
 * @param <T>
 */
public abstract class TableObjectReader<T extends AbstractSchemaObject<? super T>> extends AbstractNamedMetadataReader<T, Table>{

	protected TableObjectReader(Dialect dialect) {
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
		Object obj=context.get(SchemaProperties.TABLE_NAME.getLabel());
		if (obj==null){
			return null;
		}
		if (obj instanceof String){
			return (String)obj;
		}
		if (obj instanceof Collection){
			return (String)CommonUtils.first((Collection<?>)obj);
		}
		if (obj.getClass().isArray()){
			int size=Array.getLength(obj);
			if (size>0){
				return (String)Array.get(obj, 0);
				
			}
		}
		return null;
	}
	
	/**
	 * コンテキストへテーブル名の設定を行います
	 * @param context
	 * @param tableName
	 */
	protected void setTableName(ParametersContext context, String tableName){
		context.put(SchemaProperties.TABLE_NAME.getLabel(), tableName);
	}

	/**
	 * リストからTripleKeyMapに変換します
	 * @param list
	 */
	protected abstract TripleKeyMap<String, String, String, List<T>> toKeyMap(List<T> list);
	
	@Override
	protected boolean filterObject(T obj){
		return true;
	}
}
