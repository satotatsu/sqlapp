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

import java.util.Map;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.parameter.ParameterDefinition;
import com.sqlapp.jdbc.sql.BindParameter;
import com.sqlapp.jdbc.sql.ParameterDirection;
import com.sqlapp.jdbc.sql.SqlParameterCollection;

import static com.sqlapp.util.CommonUtils.*;

/**
 * バインド変数用の要素
 * 
 */
public class BindVariableNode extends AbstractColumnNode{
	
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1700573849729755073L;

	private final BindParameter bindParameter=new BindParameter();

	@Override
    public boolean eval(Object context, SqlParameterCollection sqlParameters) {
        BindParameter parameter = this.bindParameter.clone();
    	Object val=evalExpression(bindParameter.getName(), context);
        parameter.setValue(val);
        String operatorText=this.getColumnOperator(bindParameter.getName(), context);
        addColumnOperator(sqlParameters, operatorText);
        sqlParameters.add(parameter);
        return true;
    }

    @Override
    public void setExpression(String expression) {
		this.setParameterDefinition(parse(bindParameter, expression));
		if (this.getParameterDefinition()!=null){
			this.expression=this.getParameterDefinition().getName();
		}
	}

    /**
     * 
     * @param expression
     */
    protected static ParameterDefinition parse(BindParameter bindParameter, String expression){
    	String[] params=trim(expression).split("[ ]*;[ ]*");
    	bindParameter.setName(trim(params[0]));
    	Map<String, String> map=parseKeyValue(expression);
    	for(Map.Entry<String, String> entry:map.entrySet()){
    		if ("type".equalsIgnoreCase(entry.getKey())){
    			DataType type=DataType.valueOf(entry.getValue());
    			bindParameter.setType(type);
    		} else if ("direction".equalsIgnoreCase(entry.getKey())){
    			ParameterDirection direction=ParameterDirection.valueOf(entry.getValue());
    			bindParameter.setDirection(direction);
    		}
    	}
    	return new ParameterDefinition(bindParameter.getName());
    }

	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
    @Override
    public BindVariableNode clone(){
		return (BindVariableNode)super.clone();
	}

}
