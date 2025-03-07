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

import static com.sqlapp.util.CommonUtils.array;
import static com.sqlapp.util.CommonUtils.emptyToNull;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.DbUtils.close;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SynonymReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Synonym;
import com.sqlapp.jdbc.ExResultSet;

/**
 * DerbyのSynonym読み込み
 * 
 * @author satoh
 * 
 */
public class DerbySynonymReader extends SynonymReader {

	protected DerbySynonymReader(Dialect dialect) {
		super(dialect);
	}

	/**
	 * テーブルタイプ "TABLE","VIEW", "SYSTEM TABLE", "GLOBAL TEMPORARY", "LOCAL
	 * TEMPORARY", "ALIAS", "SYNONYM".
	 */
	private String[] tableTypes = new String[] { "ALIAS", "SYNONYM" };

	protected String[] getTableTypes() {
		return tableTypes;
	}

	@Override
	protected List<Synonym> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		List<Synonym> result = list();
		ExResultSet rs = null;
		try {
			DatabaseMetaData databaseMetaData = connection.getMetaData();
			rs = new ExResultSet(databaseMetaData.getTables(emptyToNull(this.getCatalogName(context)),
					emptyToNull(this.getSchemaName()), this.getObjectName(context), array("ALIAS", "SYNONYM")));
			// List<Map<String, Object>> rsMeta=getResultSetMetadata(rs);
			while (rs.next()) {
				String tableName = getString(rs, TABLE_NAME);
				// String tableType = getString(rs, "TABLE_TYPE");
				// String remarks = getString(rs, "REMARKS");
				Synonym synonym = new Synonym(tableName);
				synonym.setCatalogName(getString(rs, "TABLE_CAT"));
				synonym.setSchemaName(getString(rs, "TABLE_SCHEM"));
				// TODO シノニム情報は完璧には取得出来ない。
				// synonym.setRemarks(remarks);
				result.add(synonym);
			}
			return result;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			close(rs);
		}
	}

}
