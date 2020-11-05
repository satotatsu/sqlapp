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
package com.sqlapp.data.db.metadata;

import com.sqlapp.util.ToStringBuilder;

/**
 * Foreign Key Constraint 生成時に一時的に使用するクラス
 * @author satoh
 *
 */
public class ColumnPair {
	public String columnName;
	public String refColumnName;
	public String refTableName;
	public String refSchemaName;
	public String refCatalogName;
	
	@Override
	public String toString(){
		ToStringBuilder builder=new ToStringBuilder();
		builder.add("columnName", columnName);
		builder.add("refColumnName", refColumnName);
		builder.add("refTableName", refTableName);
		builder.add("refSchemaName", refSchemaName);
		builder.add("refCatalogName", refCatalogName);
		return builder.toString();
	}
}
