/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.oracle.metadata;

import java.sql.SQLException;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;

public class Oracle12cColumnReader extends Oracle11gColumnReader {

	protected Oracle12cColumnReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("columns12c.sql");
	}

	@Override
	protected Column createColumn(ExResultSet rs) throws SQLException {
		Column column = super.createColumn(rs);
		String generationType = getString(rs, "GENERATION_TYPE");
		if (generationType!=null){
			column.setIdentity(true);
			column.setIdentityGenerationType(generationType);
			String identityOptions = getString(rs, "IDENTITY_OPTIONS");
			setIdentityOptions(column, identityOptions);
		}
		return column;
	}
	
	private void setIdentityOptions(Column column, String identityOptions){
		if (identityOptions==null){
			return ;
		}
		String[] splits=identityOptions.split("[,]");
		for(String split:splits){
			split=CommonUtils.trim(split);
			String[] keyValue=split.split(":");
			String key=keyValue[0];
			String value=CommonUtils.trim(keyValue[1]);
			if ("START WITH".equalsIgnoreCase(key)){
				column.setIdentityStartValue((Number)Converters.getDefault().convertObject(value, column.getDataType().getDefaultClass()));
			}
			if ("INCREMENT BY".equalsIgnoreCase(key)){
				column.setIdentityStep((Number)Converters.getDefault().convertObject(value, column.getDataType().getDefaultClass()));
			}
			if ("MAX_VALUE".equalsIgnoreCase(key)){
				column.getSequence().setMaxValue((Number)Converters.getDefault().convertObject(value, column.getDataType().getDefaultClass()));
			}
			if ("MIN_VALUE".equalsIgnoreCase(key)){
				column.getSequence().setMinValue((Number)Converters.getDefault().convertObject(value, column.getDataType().getDefaultClass()));
			}
			if ("CYCLE_FLAG".equalsIgnoreCase(key)){
				column.getSequence().setCycle(Converters.getDefault().convertObject(value, Boolean.class));
			}
			if ("CACHE_SIZE".equalsIgnoreCase(key)){
				column.getSequence().setCacheSize((Number)Converters.getDefault().convertObject(value, column.getDataType().getDefaultClass()));
			}
			if ("ORDER_FLAG".equalsIgnoreCase(key)){
				column.getSequence().setOrder(Converters.getDefault().convertObject(value, Boolean.class));
			}
		}
	}
	
}
