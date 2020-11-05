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

import com.sqlapp.exceptions.ExpressionExecutionException;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
/**
 * SQLコメントのelse If要素
 * 
 */
public class ElseIfNode extends NeedsEndNode implements Cloneable{
	
    /**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;
   
    @Override
    public boolean eval(Object context
        , SqlParameterCollection sqlParameters){
    	boolean eval=evalBoolean(context);
        if (eval) {
            if (this.getExpression() != null){
                sqlParameters.addSql(this.getExpression());
            }
            return true;
        }
        return false;
    }
    
	protected boolean evalBoolean(Object context){
        try{
        	return getEvaluator().getEvalExecutor(this.getExpression()).evalBoolean(context);
        } catch (ExpressionExecutionException e){
        	throw handleExceptrion(e);
        }
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
    @Override
	public ElseIfNode clone(){
		return (ElseIfNode)super.clone();
	}
}
