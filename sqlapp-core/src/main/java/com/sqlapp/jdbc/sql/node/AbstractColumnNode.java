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

import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.SqlComparisonOperator;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.util.CommonUtils;

/**
 * IN 句で利用する複数のバインド変数のノード
 * @author satoh
 *
 */
public abstract class AbstractColumnNode extends CommentNode implements Cloneable{
    /**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 8430153028619529776L;
	/**カラム*/
	private String column = null;
	/**式の前の演算子*/
	private String operator = null;

	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
    @Override
	public AbstractColumnNode clone(){
		return (AbstractColumnNode)super.clone();
	}
    

    /**
	 * @return the column
	 */
    public String getColumn() {
		return column;
	}


	/**
	 * @param column the column to set
	 */
    public void setColumn(String column) {
		this.column = column;
	}


	/**
	 * @return the operator
	 */
	public String getOperator() {
		return operator;
	}

	/**
	 * @param operator the operator to set
	 */
	public void setOperator(String operator) {
		this.operator = operator;
	}
	
	protected void addColumnOperator(final SqlParameterCollection sqlParameters, String operator) {
        if (!CommonUtils.isEmpty(this.getColumn())&&!CommonUtils.isEmpty(this.getOperator())){
            sqlParameters.addSql(this.getColumn());
            sqlParameters.addSql(' ');
            sqlParameters.addSql(operator);
            sqlParameters.addSql(' ');
        }
	}

	protected String getColumnOperator(String parameterName, Object context) {
		String operator=null;
		if (!CommonUtils.isEmpty(this.getColumn())&&!CommonUtils.isEmpty(this.getOperator())){
			if (context instanceof ParametersContext){
				ParametersContext parametersContext=ParametersContext.class.cast(context);
				SqlComparisonOperator dynamicOperator=parametersContext.getOperator(parameterName);
				if (dynamicOperator!=null){
					operator=dynamicOperator.getDisplayName();
				} else{
					operator=this.getOperator();
				}
			} else{
				operator=this.getOperator();
			}
		}
		return operator;
	}

}