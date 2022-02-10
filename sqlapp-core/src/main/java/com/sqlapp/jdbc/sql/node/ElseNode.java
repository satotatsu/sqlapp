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

public class ElseNode extends CommentNode implements Cloneable{
    /**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6298944681738721037L;

    @Override
    public boolean eval(Object context
        , SqlParameterCollection sqlParameters){
        if (this.getExpression() != null){
            sqlParameters.addSql(this.getExpression());
        }
        return true;
    }
    
	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
    @Override
    public ElseNode clone(){
		return (ElseNode)super.clone();
	}
}
