/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.data.db.metadata;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.AbstractDbTest;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Table;

class MetaDataReaderTest extends AbstractDbTest {

	/**
	 * INSERTして自動生成されたキーを使って、そのままUPDATEを行う
	 * 
	 * @throws SQLException
	 */
	@Test
	void testInsertUpdateWithGeneratedKey() throws SQLException {
		final String sql = this.getResource("create_table1.sql");
		testDb(connection -> {
			this.dropTables(connection, "TAB1");
			executeSql(connection, sql);
			Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			TableReader tableReader = dialect.getCatalogReader().getSchemaReader().getTableReader();
			tableReader.setSchemaName("PUBLIC");
			tableReader.setObjectName("TAB1");
			List<Table> tables = tableReader.getAllFull(connection);
			assertTrue(tables.size() > 0);
		}, (connection) -> {
			this.dropTables(connection, "TAB1");
		});
	}
}
