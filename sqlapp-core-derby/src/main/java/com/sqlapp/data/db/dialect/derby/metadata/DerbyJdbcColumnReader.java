/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-derby.
 *
 * sqlapp-core-derby is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-derby is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-derby.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.derby.metadata;

import java.math.BigInteger;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcColumnReader;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.jdbc.ExResultSet;
/**
 * Derby用のカラム読み込み
 * @author satoh
 *
 */
public class DerbyJdbcColumnReader extends JdbcColumnReader{

	protected DerbyJdbcColumnReader(Dialect dialect) {
		super(dialect);
	}
	

	//AUTOINCREMENT: start 1 increment 1
	/**
	 * AUTOINCREMENTの場合のデフォルトパターン
	 */
	private static final Pattern AUTOINCREMENT_PETTERN=Pattern.compile("AUTOINCREMENT:[\\s]+(start\\s+[0-9]+)[\\s]*(increment\\s+[0-9]+)[\\s]*", Pattern.CASE_INSENSITIVE);
	
	@Override
	protected Column createColumn(ExResultSet rs) throws SQLException{
		Column column=super.createColumn(rs);
		if (column.getDefaultValue()!=null){
			Matcher matcher=AUTOINCREMENT_PETTERN.matcher(column.getDefaultValue());
			if (matcher.matches()){
				int i=1;
				String st=matcher.group(i++);
				String ic=matcher.group(i++);
				Converters.getDefault().convertObject(st.replace("start", ""), BigInteger.class);
				column.setIdentity(true).setIdentityStartValue(Converters.getDefault().convertObject(st.replace("start", ""), BigInteger.class));
				column.setIdentityStep(Converters.getDefault().convertObject(ic.replace("increment", ""), BigInteger.class));
				column.setDefaultValue(null);
			}
		}
		return column;
	}
			

}
