/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.oracle.metadata;

import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.TableSpace;
import com.sqlapp.jdbc.ExResultSet;
/**
 * Oracleのテーブルスペース読み込みクラス
 * @author satoh
 *
 */
public class Oracle11gR2TableSpaceReader extends OracleTableSpaceReader{

	protected Oracle11gR2TableSpaceReader(Dialect dialect) {
		super(dialect);
	}
	
	@Override
	protected TableSpace createTableSpace(ExResultSet rs) throws SQLException{
		TableSpace tableSpace=super.createTableSpace(rs);
		setSpecifics(rs, "PREDICATE_EVALUATION", tableSpace);
		setSpecifics(rs, "ENCRYPTED", tableSpace);
		setSpecifics(rs, "COMPRESS_FOR", tableSpace);
		return tableSpace;
	}
}
