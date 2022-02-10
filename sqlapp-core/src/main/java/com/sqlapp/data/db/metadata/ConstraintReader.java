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
import com.sqlapp.data.schemas.Constraint;
import com.sqlapp.data.schemas.ConstraintCollection;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.TripleKeyMap;
import static com.sqlapp.util.CommonUtils.*;

public abstract class ConstraintReader<T extends Constraint> extends TableObjectReader<T>{
	/**
	 * 更新ルール
	 */
	protected static final String UPDATE_RULE="update_rule";
	/**
	 * 削除ルール
	 */
	protected static final String DELETE_RULE="delete_rule";
	
	protected static final String MATCH_OPTION="match_option";
	
	protected static final String DEFERRABILITY="deferrability";
	
	protected ConstraintReader(Dialect dialect) {
		super(dialect);
	}

	/**
	 * 制約名
	 */
	private String constraintName=null;
	
	public String getConstraintName() {
		return constraintName;
	}

	public void setConstraintName(String constraintName) {
		this.constraintName = constraintName;
	}

	@Override
	public void loadFull(Connection connection, Table table) {
		List<T> list=getAllFull(connection);
		int size=list.size();
		List<Constraint> c=getSchemaObjectList(table);
		for(int i=0;i<size;i++){
			Constraint obj=list.get(i);
			c.add(obj);
		}
	}

	@Override
	public void load(Connection connection, Table table) {
		List<T> list=getAll(connection);
		int size=list.size();
		List<Constraint> c=getSchemaObjectList(table);
		for(int i=0;i<size;i++){
			Constraint obj=list.get(i);
			c.add(obj);
		}
	}
	
	/**
	 * リストからTripleKeyMapに変換します
	 * @param list
	 */
	@Override
	protected TripleKeyMap<String, String, String, List<T>> toKeyMap(List<T> list){
		TripleKeyMap<String, String, String, List<T>> map=new TripleKeyMap<String, String, String, List<T>>();
		for(T obj:list){
			List<T> tableConsts=map.get(obj.getCatalogName(), obj.getSchemaName(), obj.getTableName());
			if (tableConsts==null){
				tableConsts=list();
				map.put(obj.getCatalogName(), obj.getSchemaName(), obj.getTableName(), tableConsts);
			}
			tableConsts.add(obj);
		}
		return map;
	}

	/**
	 * カタログ名、スキーマ名、テーブル名、制約名を含むパラメタコンテキストを作成します。
	 */
	@Override
	protected ParametersContext defaultParametersContext(Connection connection){
		ParametersContext context=newParametersContext(connection, this.getCatalogName(), this.getSchemaName());
		this.setTableName(context, nativeCaseString(connection,  this.getObjectName()));
		context.put(getNameLabel(), nativeCaseString(connection,  this.getConstraintName()));
		return context;
	}
	
	@Override
	protected String getNameLabel(){
		return "constraintName";
	}
	
	protected ConstraintCollection getSchemaObjectList(Table table) {
		return table.getConstraints();
	}
}
