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
import static com.sqlapp.util.CommonUtils.*;

import java.util.Map;

import com.sqlapp.data.parameter.ParameterDefinition;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.util.AbstractIterator;
import com.sqlapp.util.SimpleBeanUtils;
/**
 * SQLコメントのFor要素
 * 
 */
public class ForNode extends NeedsEndNode implements Cloneable{

	/** serialVersionUID */
	private static final long serialVersionUID = -1261063598891910651L;
	/**
	 * 変数名
	 */
	private String variableName=null;

    @Override
    public boolean eval(Object context
        , SqlParameterCollection sqlParameters){
        Object val=evalExpression(this.getExpression(), context);
        if (context instanceof ParametersContext){
        	execLoop((ParametersContext)context, sqlParameters, val);
        }else if (context instanceof Map){
        	execLoop((Map<?,?>)context, sqlParameters, val);
        } else if (context!=null){
        	Map<String, Object> map=SimpleBeanUtils.getInstance(context.getClass()).toMap(context);
        	execLoop(map, sqlParameters, val);
        } else{
        	Map<String, Object> map=map();
        	execLoop(map, sqlParameters, val);
        }
        return true;
    }

	private void execLoop(final ParametersContext context, final SqlParameterCollection sqlParameters, Object val) {
        final String variableName = this.getVariableName();
        final String indexName=getIndexName();
        final ParametersContext copyContext=context.clone();
		AbstractIterator<Object> itr=new AbstractIterator<Object>(this.getEvaluator()){
			@Override
			protected void handle(Object obj, int index) {
				copyContext.put(indexName, index);
				copyContext.put(variableName, obj);
	            evalChilds(copyContext, sqlParameters);
			}
			@Override
			protected void executeFinally(){
				copyContext.remove(variableName);
				copyContext.remove(indexName);
			}
		};
		try {
			itr.execute(val);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
    }

	private void execLoop(final Map<?,?> context, final SqlParameterCollection sqlParameters, Object val) {
        final String variableName = this.getVariableName();
        final String indexName=getIndexName();
        @SuppressWarnings("rawtypes")
		final Map copyContext=cloneMap(context);
        final boolean containts=context.containsKey(variableName);
		AbstractIterator<Object> itr=new AbstractIterator<Object>(this.getEvaluator()){
			@SuppressWarnings("unchecked")
			@Override
			protected void handle(Object obj, int index) {
				copyContext.put(indexName, index);
				if (!containts){
					copyContext.put(variableName, obj);
				}
	            evalChilds(copyContext, sqlParameters);
			}
			@Override
			protected void executeFinally(){
				if (!containts){
					copyContext.remove(variableName);
				}
				copyContext.remove(indexName);
			}
		};
		try {
			itr.execute(val);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
    }

	protected String getIndexName(){
        return this.getVariableName()+"_index";
	}

	/**
	 * @return the variableName
	 */
	public String getVariableName() {
		return variableName;
	}

	/**
	 * @param variableName the variableName to set
	 */
	protected void setVariableName(String variableName) {
		this.variableName = variableName;
		this.setParameterDefinition(new ParameterDefinition(variableName));
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
    @Override
	public ForNode clone(){
		return (ForNode)super.clone();
	}
}
