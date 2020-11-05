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

import com.sqlapp.jdbc.sql.BindParameter;
import com.sqlapp.jdbc.sql.SqlComparisonOperator;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.util.AbstractIterator;
import com.sqlapp.util.CommonUtils;

/**
 * IN 句で利用する複数のバインド変数のノード
 * 
 * @author satoh
 *
 */
public class BindVariableArrayNode extends AbstractColumnNode {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 8430153028619529776L;

	private BindParameter bindParameter = new BindParameter();

	@Override
	public void setExpression(String expression) {
		this.setParameterDefinition(BindVariableNode.parse(bindParameter, expression));
		if (this.getParameterDefinition() != null) {
			this.expression = this.getParameterDefinition().getName();
		}
	}

	@Override
	public boolean eval(Object context, SqlParameterCollection sqlParameters) {
		Object val = evalExpression(bindParameter.getName(), context);
		addValues(sqlParameters, context, val);
		return true;
	}

	static final int SPLIT_SIZE = 1000;

	/**
	 * SqlParameterCollectionに値を追加する
	 * 
	 * @param sqlParameters
	 * @param val
	 */
	private void addValues(final SqlParameterCollection sqlParameters, Object context, Object val) {
		final BindParameter originalParameter = this.bindParameter;
		String operatorText = this.getColumnOperator(bindParameter.getName(), context);
		List<BindParameter> parameters = CommonUtils.list();
		final SqlComparisonOperator operator=SqlComparisonOperator.parse(operatorText);
		AbstractIterator<Object> itr = new AbstractIterator<Object>(this.getEvaluator(), bindParameter.getName()) {
			@Override
			protected void handle(Object obj, int index) {
				BindParameter parameter = originalParameter.clone();
				if (operator!=null){
					parameter.setValue(operator.getConverter().apply(obj));
				} else{
					parameter.setValue(obj);
				}
				parameters.add(parameter);
			}
		};
		try {
			itr.execute(val);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		buildColumnOperator(operatorText, parameters, sqlParameters);
	}
	
	private void buildColumnOperator(String operatorText, List<BindParameter> parameters, final SqlParameterCollection sqlParameters){
		SqlComparisonOperator operator=SqlComparisonOperator.parse(operatorText);
		if (parameters.size()>1){
			if (operator!=null&&operator.allowMultiple()){
				buildMultipleColumnOperator(operator, parameters, sqlParameters);
			}else if (operator!=null&&operator.getMultipleOperator()!=null){
				operator=operator.getMultipleOperator();
				buildMultipleColumnOperator(operator, parameters, sqlParameters);
			}else if (operator!=null&&operator.getParameterCount()!=null){
				if (operator.getOperaterElements()!=null){
					//a <= x < b
					String conjuction=operator.conjuction();
					sqlParameters.addSql(" ( ");
					if (parameters.size()!=operator.getOperaterElements().length){
						throw new IllegalArgumentException("name="+this.getColumn()+",parameter.size="+parameters.size()+", acceptableSize="+operator.getOperaterElements().length);
					}
					for(int i=0;i<operator.getOperaterElements().length;i++){
						sqlParameters.addSql(conjuction, i> 0);
						SqlComparisonOperator op=operator.getOperaterElements()[i];
						addColumnOperator(sqlParameters, op.getSqlValue());
						BindParameter param=parameters.get(i);
						sqlParameters.add(param);
					}
					sqlParameters.addSql(" ) ");
				} else{
					//BETWEEN
					addColumnOperator(sqlParameters, operator.getSqlValue());
					String conjuction=operator.conjuction();
					for(int i=0;i<parameters.size();i++){
						sqlParameters.addSql(conjuction, i> 0);
						BindParameter param=parameters.get(i);
						sqlParameters.add(param);
					}
				}
			} else{
				if (operator!=null&&operator.isNegationOperator()&&operator.reverse()!=null){
					operator=operator.reverse();
					sqlParameters.addSql(" NOT ( ");
				} else{
					sqlParameters.addSql(" ( ");
				}
				for (int i = 0; i < parameters.size(); i++) {
					sqlParameters.addSql(" OR ", i> 0);
					if (operator==null){
						addColumnOperator(sqlParameters, operatorText);
					} else{
						addColumnOperator(sqlParameters, operator.getSqlValue());
					}
					BindParameter param = parameters.get(i);
					sqlParameters.add(param);
				}
				sqlParameters.addSql(" ) ");
			}
		} else if (parameters.size()==1){
			if (operator==null){
				addColumnOperator(sqlParameters, operatorText);
				sqlParameters.add(CommonUtils.first(parameters));
			} else{
				addColumnOperator(sqlParameters, operator.getSqlValue());
				if (operator.allowMultiple()){
					sqlParameters.addSql('(');
					sqlParameters.addAll(parameters);
					sqlParameters.addSql(')');
				} else{
					sqlParameters.add(CommonUtils.first(parameters));
				}
			}
		}
	}

	private void buildMultipleColumnOperator(SqlComparisonOperator operator, List<BindParameter> parameters, final SqlParameterCollection sqlParameters){
		if (parameters.size() <= SPLIT_SIZE) {
			addColumnOperator(sqlParameters, operator.getSqlValue());
			sqlParameters.addSql('(');
			sqlParameters.addAll(parameters);
			sqlParameters.addSql(')');
		}else{
			List<List<BindParameter>> splits = splitBindParameters(parameters, SPLIT_SIZE);
			sqlParameters.addSql(" ( ");
			for (int i = 0; i < splits.size(); i++) {
				if (i > 0) {
					sqlParameters.addSql(" OR ");
				}
				addColumnOperator(sqlParameters, operator.getSqlValue());
				sqlParameters.addSql('(');
				List<BindParameter> params = splits.get(i);
				sqlParameters.addAll(params);
				sqlParameters.addSql(')');
			}
			sqlParameters.addSql(" ) ");
		}
	}

	private List<List<BindParameter>> splitBindParameters(List<BindParameter> parameters, int size) {
		List<List<BindParameter>> result = CommonUtils.list();
		List<BindParameter> current = CommonUtils.list();
		result.add(current);
		for (int i = 0; i < parameters.size(); i++) {
			BindParameter parameter = parameters.get(i);
			current.add(parameter);
			if (current.size() >= size) {
				current = CommonUtils.list();
				result.add(current);
			}
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@Override
	public BindVariableArrayNode clone() {
		return (BindVariableArrayNode) super.clone();
	}
}