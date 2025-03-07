/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.util;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.jdbc.sql.ParameterDirection;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * DB2用のSQLビルダー
 * 
 * @author tatsuo satoh
 * 
 */
public class Db2SqlBuilder extends AbstractSqlBuilder<Db2SqlBuilder> {

	public Db2SqlBuilder(Dialect dialect) {
		super(dialect);
	}

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/* (non-Javadoc)
	 * @see com.sqlapp.util.AbstractSqlBuilder#count()
	 */
	@Override
	public Db2SqlBuilder count(){
		appendElement("COUNT_BIG");
		return instance();
	}
	
	
	public Db2SqlBuilder starting(){
		appendElement("STARTING");
		return instance();
	}
	
	public Db2SqlBuilder ending(){
		appendElement("ENDING");
		return instance();
	}
	
	
	public Db2SqlBuilder _long(){
		appendElement("LONG");
		return instance();
	}
	
	public Db2SqlBuilder mask(){
		appendElement("MASK");
		return instance();
	}

	public Db2SqlBuilder argument(NamedArgument obj) {
		argumentBefore(obj);
		if (obj.getName() != null) {
			this._add(obj.getName());
			this.space();
		}
		if (obj.getDirection() != null
				&& obj.getDirection() != ParameterDirection.Input) {
			this._add(obj.getDirection());
			this.space();
		}
		this.typeDefinition(obj);
		argumentAfter(obj);
		return instance();
	}
	
	protected void argumentBefore(NamedArgument obj) {
	}
	
	@Override
	public Db2SqlBuilder clone(){
		return (Db2SqlBuilder)super.clone();
	}
}
