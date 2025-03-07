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

package com.sqlapp.data.db.sql;

import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.DbObjectDifferenceCollection;
import com.sqlapp.data.schemas.State;
import com.sqlapp.util.CommonUtils;

public interface SqlFactoryRegistry {

	/**
	 * SqlOperationを生成します
	 * 
	 * @param dbObject
	 * @param sqlType
	 * @return SqlOperation
	 */
	default <T extends DbCommonObject<?>> List<SqlOperation> createSql(T dbObject,
			SqlType sqlType){
		SqlFactory<T> sqlFactory=getSqlFactory(dbObject, sqlType);
		return sqlFactory.createSql(dbObject);
	}

	/**
	 * SqlOperationを生成します
	 * 
	 * @param sqlType
	 * @return SqlOperation
	 */
	default List<SqlOperation> createSql(SqlType sqlType){
		SqlFactory<?> sqlFactory=getSqlFactory(sqlType);
		return sqlFactory.createSql();
	}

	/**
	 * SqlOperationを生成します
	 * 
	 * @param sqlType
	 * @return SqlOperation
	 */
	default List<SqlOperation> createSql(
			DbObjectDifference difference, SqlType sqlType){
		SqlFactory<?> sqlFactory=getSqlFactory(difference, sqlType);
		return sqlFactory.createDiffSql(difference);
	}

	/**
	 * SqlOperationを生成します
	 * 
	 * @param sqlType
	 * @return SqlOperation
	 */
	default<T extends DbCommonObject<?>> List<SqlOperation> createSql(T dbObject,
			State state){
		SqlFactory<T> sqlFactory=getSqlFactory(dbObject, state);
		return sqlFactory.createSql(dbObject);
	}

	/**
	 * Difference用Operationを取得します
	 * 
	 * @return Operation
	 */
	default List<SqlOperation> createSql(
			DbObjectDifference difference){
		SqlFactory<?> sqlFactory=getSqlFactory(difference);
		return sqlFactory.createDiffSql(difference);
	}

	/**
	 * Difference用Operationを取得します
	 * 
	 * @return Operation
	 */
	default List<SqlOperation> createSql(
			DbObjectDifferenceCollection differences){
		List<SqlOperation> list=CommonUtils.list();
		for(DbObjectDifference diff:differences.getList(State.Deleted)){
			list.addAll(this.createSql(diff));
		}
		for(DbObjectDifference diff:differences.getList(State.Added, State.Modified)){
			list.addAll(this.createSql(diff));
		}
		return list;
	}

	/**
	 * SqlFactoryを取得します
	 * 
	 * @param sqlType
	 * @return Operation
	 */
	<T extends DbCommonObject<?>, U extends SqlFactory<?>> U getSqlFactory(T dbObject,
			SqlType sqlType);

	/**
	 * SqlFactoryを取得します
	 * 
	 * @param sqlType
	 */
	SqlFactory<?> getSqlFactory(SqlType sqlType);

	/**
	 * Difference用SqlFactoryを取得します
	 * 
	 * @param sqlType
	 * @return SqlOperation
	 */
	<U extends SqlFactory<?>> U getSqlFactory(
			DbObjectDifference difference, SqlType sqlType);

	/**
	 * SqlFactoryを取得します
	 * 
	 * @param state
	 * @return Operation
	 */
	<T extends DbCommonObject<?>, U extends SqlFactory<?>> U getSqlFactory(T dbObject,
			State state);

	/**
	 * Difference用Operationを取得します
	 * 
	 * @return Operation
	 */
	<U extends SqlFactory<?>> U getSqlFactory(
			DbObjectDifference difference);

	Dialect getDialect();

	/**
	 * Registr SQL Facroty
	 * 
	 * @param sqlType
	 * @param sqlFactoryClass
	 */
	void registerSqlFactory(SqlType sqlType,
			Class<? extends SqlFactory<?>> sqlFactoryClass);
	
	/**
	 * Registr SQL Facroty
	 * 
	 * @param objectClass
	 *            登録対象のDBオブジェクトクラス名
	 * @param sqlType
	 * @param sqlFactoryClass
	 */
	void registerSqlFactory(Class<?> objectClass, SqlType sqlType,
			Class<? extends SqlFactory<?>> sqlFactoryClass);

	/**
	 * De Registr SQL Facroty
	 * 
	 * @param sqlType
	 */
	void deregisterSqlFactory(SqlType sqlType);

	/**
	 * De Registr SQL Facroty
	 * 
	 * @param objectClass
	 *            削除対象のDBオブジェクトクラス名
	 * @param sqlType
	 */
	void deregisterSqlFactory(Class<?> objectClass, SqlType sqlType);
	/**
	 * De Registr SQL Facroty
	 * 
	 * @param objectClass
	 *            削除対象のDBオブジェクトクラス名
	 * @param sqlType
	 */
	void deregisterSqlFactory(Class<?> objectClass, SqlType... sqlType);
	/**
	 * De Registr SQL Facroty
	 * 
	 * @param objectClass
	 *            削除対象のDBオブジェクトクラス名
	 */
	void deregisterSqlFactory(Class<?> objectClass);

	/**
	 * 既定のオプションを取得します
	 * 
	 * @return オプション
	 */
	Options getOption();

	/**
	 * オプションを登録します
	 * 
	 * @param オプション
	 */
	void setOption(Options option);

}
