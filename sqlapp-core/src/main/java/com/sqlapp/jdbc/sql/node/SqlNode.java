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

package com.sqlapp.jdbc.sql.node;

import java.util.List;
import java.util.Set;

import com.sqlapp.data.parameter.ParameterDefinition;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.util.CommonUtils;

public class SqlNode extends Node {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;
	
	public SqlNode(){
		
	}

	public SqlNode(String sql){
		SqlPartNode sqlPart=new SqlPartNode();
		sqlPart.setSql(sql);
		this.addChildNode(sqlPart);
	}

	@Override
	public boolean eval(Object context, SqlParameterCollection sqlParameters) {
		List<Node> nodes = getChildNodes();
		int size = nodes.size();
		for (int i = 0; i < size; i++) {
			Node child = nodes.get(i);
			child.eval(context, sqlParameters);
		}
		return true;
	}
	
	private Set<ParameterDefinition> parameters;

	/**
	 * @return the parameters
	 */
	public Set<ParameterDefinition> getParameters() {
		return parameters;
	}

	/**
	 * @param parameters the parameters to set
	 */
	public void setParameters(Set<ParameterDefinition> parameters) {
		this.parameters = parameters;
	}

	/**
	 * offsetパラメータを取得します。
	 * @return offsetパラメータ
	 */
	public ParameterDefinition getOffsetParameter(){
		return getParameterByName(ParameterDefinition.OFFSET_KEY_PARANETER_NAME);
	}

	/**
	 * rowパラメータを取得します。
	 * @return rowパラメータ
	 */
	public ParameterDefinition getRowParameter(){
		return getParameterByName(ParameterDefinition.ROW_KEY_PARANETER_NAME);
	}

	/**
	 * countSqlパラメータを取得します。
	 * @return countSqlパラメータ
	 */
	public ParameterDefinition getCountSqlParameter(){
		return getParameterByName(ParameterDefinition.COUNTSQL_KEY_PARANETER_NAME);
	}

	/**
	 * 指定した名前のパラメータを取得します。
	 * @param name パラメータ名
	 * @return パラメータ
	 */
	public ParameterDefinition getParameterByName(String name){
		for(ParameterDefinition def:this.parameters){
			if (name.equals(def.getName())){
				return def;
			}
		}
		return null;
	}
	
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@Override
	public SqlNode clone() {
		SqlNode clone=SqlNode.class.cast(super.clone());
		if (this.getParameters()!=null){
			clone.parameters=CommonUtils.linkedSet();
			for(ParameterDefinition def:this.getParameters()){
				clone.getParameters().add(def.clone());
			}
		}
		return clone;
	}

	@Override
	public String toString(){
		StringBuilder builder=new StringBuilder();
		List<Node> nodes = getChildNodes();
		int size = nodes.size();
		for (int i = 0; i < size; i++) {
			Node child = nodes.get(i);
			builder.append(child.toString());
		}
		return builder.toString();
	}
	
}
