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

package com.sqlapp.data.schemas.properties;

import java.util.List;

import com.sqlapp.util.CommonUtils;

/**
 * Definition IF
 * @author satoh
 *
 */
public interface StatementSetter<T>{

	/**
	 * statementを設定します
	 * 
	 * @param statement
	 */
	T setStatement(List<String> statement);

	/**
	 * statementを設定します
	 * 
	 * @param statement
	 */
	@SuppressWarnings("unchecked")
	default T setStatement(String statement){
		setStatement(CommonUtils.splitLine(statement));
		return (T)this;
	}

}
