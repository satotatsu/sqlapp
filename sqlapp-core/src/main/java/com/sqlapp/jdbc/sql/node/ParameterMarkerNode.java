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

package com.sqlapp.jdbc.sql.node;

import java.util.Map;

import com.sqlapp.data.parameter.ParameterDefinition;
import com.sqlapp.jdbc.sql.SqlParameterCollection;

import static com.sqlapp.util.CommonUtils.*;

/**
 * パラメータ用の要素
 * 
 */
public class ParameterMarkerNode extends CommentNode {
	
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1700573849729755073L;
	/**
	 * パラメータ名
	 */
	private String parameterName;
	/**
	 * パラメータタイプ
	 */
	private String parameterType;
	
    @Override
    public boolean eval(Object context, SqlParameterCollection sqlParameters) {
        return true;
    }

    @Override
    public void setExpression(String expression) {
    	Map<String, String> map=parseKeyValue(expression);
    	for(Map.Entry<String, String> entry:map.entrySet()){
    		if ("name".equalsIgnoreCase(entry.getKey())){
    			parameterName=entry.getValue();
    		} else if ("type".equalsIgnoreCase(entry.getKey())){
    			parameterType=entry.getValue();
    		}
    	}
    	this.setParameterDefinition(new ParameterDefinition(this.parameterName, this.parameterType));
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
    @Override
    public ParameterMarkerNode clone(){
		return (ParameterMarkerNode)super.clone();
	}

}
