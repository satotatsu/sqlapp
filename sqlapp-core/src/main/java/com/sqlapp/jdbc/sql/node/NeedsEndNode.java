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

import com.sqlapp.jdbc.sql.SqlParameterCollection;

public abstract class NeedsEndNode extends CommentNode {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 8777526137788125662L;
	
	/**
	 * 子供の全要素のevalを実施する
	 * @param context
	 * @param sqlParameters
	 */
	protected boolean evalChilds(Object context, SqlParameterCollection sqlParameters){
		int size=this.getChildNodes().size();
		boolean result=false;
        for(int i=0;i<size;i++){
        	Node element=this.getChildNodes().get(i);
            if (element instanceof SqlPartNode){
                sqlParameters.addSql(((SqlPartNode)element).getSql());
                result=true;
            } else if (element instanceof CommentNode){
                CommentNode commentElement = (CommentNode)element;
                if(commentElement.eval(context, sqlParameters)){
                    result=true;
                }
            }
        }
        return result;
	}
}
