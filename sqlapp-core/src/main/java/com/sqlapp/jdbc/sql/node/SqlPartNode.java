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

import static com.sqlapp.util.CommonUtils.isBlank;

import com.sqlapp.jdbc.sql.SqlParameterCollection;

public class SqlPartNode extends Node implements Cloneable {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -6390623757464617918L;

	@Override
	public boolean eval(Object context, SqlParameterCollection sqlParameters) {
		String sqlPart = this.getSql();
		if (!isBlank(sqlPart)) {
			sqlParameters.addSql(sqlPart);
		}
		return true;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		if (!isBlank(this.getSql())) {
			builder.append(this.getSql());
		}
		for (Node element : getChildNodes()) {
			String childSql = element.toString();
			builder.append(childSql);
		}
		return builder.toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@Override
	public SqlPartNode clone() {
		return (SqlPartNode) super.clone();
	}
}
