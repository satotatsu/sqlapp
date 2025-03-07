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

import com.sqlapp.jdbc.sql.SqlParameterCollection;

/**
 * Input Stream Node
 * 
 */
public class InputStreamNode extends CommentNode {
	
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1700573849729755073L;
	
    @Override
    public boolean eval(Object context, SqlParameterCollection sqlParameters) {
		Object val=evalExpression(this.getExpression(), context);
		sqlParameters.setInputStream(val);
        return true;
    }

    @Override
    public void setExpression(String expression) {
    	super.setExpression(expression);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
    @Override
    public InputStreamNode clone(){
		return (InputStreamNode)super.clone();
	}

}
