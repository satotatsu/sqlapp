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

package com.sqlapp.data.parameter;

import static com.sqlapp.util.CommonUtils.map;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.sqlapp.jdbc.sql.SqlComparisonOperator;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;
import com.sqlapp.util.ToStringBuilder;
/**
 * パラメタコンテキスト
 * 
 * @author SATOH
 * 
 */
public class ParametersContext implements Serializable, javax.script.Bindings,Cloneable {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -6927591564842130376L;

	private Map<String, Object> internalMap = map();

	private Map<String, SqlComparisonOperator> operatorMap = map();

	public ParametersContext(){
		resetCountSqlMode();
	}
	
	/**
	 * @return the internalMap
	 */
	protected Map<String, Object> getInternalMap() {
		return internalMap;
	}

	/**
	 * @return the operatorMap
	 */
	protected Map<String, SqlComparisonOperator> getOperatorMap() {
		return operatorMap;
	}

	/**
	 * @param internalMap the internalMap to set
	 */
	protected void setInternalMap(Map<String, Object> internalMap) {
		this.internalMap = internalMap;
	}

	/**
	 * @param operatorMap the operatorMap to set
	 */
	protected void setOperatorMap(Map<String, SqlComparisonOperator> operatorMap) {
		this.operatorMap = operatorMap;
	}

	@Override
	public boolean containsKey(Object key) {
		//return internalMap.containsKey(key);
		return true;
	}

	public boolean containsKeyInternal(Object key) {
		return internalMap.containsKey(key);
	}
	
	/**
	 * count(*)で実行するように設定します。
	 */
	public void setCountSqlMode(){
		this.put(ParameterDefinition.COUNTSQL_KEY_PARANETER_NAME, true);
	}

	/**
	 * count(*)で実行するように設定します。
	 */
	public void resetCountSqlMode(){
		this.put(ParameterDefinition.COUNTSQL_KEY_PARANETER_NAME, false);
	}

	@Override
	public Object get(Object key) {
		return get(key.toString());
	}

	public Object get(String key) {
		Object val = getInternalMap().get(key);
		return val;
	}

	@Override
	public Object put(String name, Object value) {
		return getInternalMap().put(name, value);
	}

	public void put(Object value) {
		Map<String,Object> map=SimpleBeanUtils.toMap(value);
		getInternalMap().putAll(map);
	}

	public SqlComparisonOperator getOperator(String name) {
		return getOperatorMap().get(name);
	}

	public SqlComparisonOperator putOperator(String name, SqlComparisonOperator value) {
		return getOperatorMap().put(name, value);
	}
	
	public void putOperatorValue(String name, SqlComparisonOperator op, Object arg){
		put(name, arg);
		putOperator(name, op);
	}

	public SqlComparisonOperator removeOperator(String name) {
		return getOperatorMap().remove(name);
	}

	@Override
	public void putAll(Map<? extends String, ? extends Object> toMerge) {
		getInternalMap().putAll(toMerge);
	}

	@Override
	public Object remove(Object key) {
		return getInternalMap().remove(key);
	}

	@Override
	public void clear() {
		getInternalMap().clear();
	}

	@Override
	public boolean containsValue(Object value) {
		return getInternalMap().containsValue(value);
	}

	@Override
	public Set<java.util.Map.Entry<String, Object>> entrySet() {
		return getInternalMap().entrySet();
	}

	@Override
	public boolean isEmpty() {
		return getInternalMap().isEmpty();
	}

	@Override
	public Set<String> keySet() {
		return getInternalMap().keySet();
	}

	@Override
	public int size() {
		return getInternalMap().size();
	}

	@Override
	public Collection<Object> values() {
		return getInternalMap().values();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	@Override
	public ParametersContext clone() {
		ParametersContext context=new ParametersContext();
		context.internalMap=CommonUtils.map(this.getInternalMap());
		context.operatorMap=CommonUtils.map(this.getOperatorMap());
		return context;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		ToStringBuilder builder = new ToStringBuilder();
		builder.add(getInternalMap());
		return builder.toString();
	}
}
