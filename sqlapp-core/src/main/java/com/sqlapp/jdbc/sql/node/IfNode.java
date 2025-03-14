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

import java.util.List;

import static com.sqlapp.util.CommonUtils.*;

import com.sqlapp.data.parameter.ParameterDefinition;
import com.sqlapp.exceptions.ExpressionExecutionException;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
/**
 * SQLコメントのIf要素
 * 
 */
public class IfNode extends NeedsEndNode{
	
    /**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -2951149147210439845L;

    private List<ElseIfNode> elseIfNodes = list();

    private ElseNode elseNode = null;
   
    @Override
    public boolean eval(Object context
        , SqlParameterCollection sqlParameters){
    	sqlParameters.addSql(' ');
    	boolean eval=evalBoolean(context);
        if (eval) {
            return evalChilds(context, sqlParameters);
        } else {
        	int size=elseIfNodes.size();
        	for(int i=0;i<size;i++){
        		ElseIfNode elseIfNode=elseIfNodes.get(i);
        		if(elseIfNode.eval(context, sqlParameters)){
        			return true;
        		}
        	}
            if (elseNode != null){
                return elseNode.eval(context, sqlParameters);
            }
        }
        return false;
    }
    
	public List<ElseIfNode> getElseIfNodes() {
		return elseIfNodes;
	}

	protected boolean evalBoolean(Object context){
        try{
        	return getEvaluator().getEvalExecutor(this.getExpression()).evalBoolean(context);
        } catch (ExpressionExecutionException e){
        	throw handleExceptrion(e);
        }
	}

	public ElseNode getElseNode() {
		return elseNode;
	}

	public void setElseNode(ElseNode elseNode) {
		this.elseNode = elseNode;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
    @Override
	public IfNode clone(){
		return (IfNode)super.clone();
	}
    
    @Override
	public void setExpression(String expression) {
    	super.setExpression(expression);
    	this.setParameterDefinition(new ParameterDefinition(expression));
	}
}
