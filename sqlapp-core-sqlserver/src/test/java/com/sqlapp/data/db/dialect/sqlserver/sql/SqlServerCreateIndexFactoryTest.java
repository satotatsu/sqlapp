/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.sqlserver.metadata.SqlServer2000IndexReader;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerIndexOptions;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

public class SqlServerCreateIndexFactoryTest extends AbstractSqlServer11SqlFactoryTest {
	SqlFactory<Index> operationFacroty;

	@BeforeEach
	public void before() {
		operationFacroty = this.sqlFactoryRegistry.getSqlFactory(new Index("indexA"),
				State.Added);
	}

	@Test
	public void testGetDdl() {
		final Table table = new Table("tableA");
		table.getColumns().add(
				new Column("colA").setDataType(DataType.INT).setNotNull(true));
		table.getColumns()
				.add(new Column("colB").setDataType(DataType.BIGINT).setCheck(
						"colB>0"));
		table.getColumns().add(
				new Column("colC").setDataType(DataType.VARCHAR).setLength(10)
						.setDefaultValue("0"));
		table.setPrimaryKey("PK_TABLEA", table.getColumns().get("colA"), table
				.getColumns().get("colB"));
		table.getConstraints().addUniqueConstraint("UK_tableA1",
				table.getColumns().get("colB"));
		table.getIndexes().add("IDX_tableA1", table.getColumns().get("colC"))
				.getColumns().get(0).setOrder(Order.Desc);
		final Index index = table.getIndexes().get("IDX_tableA1");
		index.setWhere("colC>1");
		index.getSpecifics().put(SqlServer2000IndexReader.FILL_FACTOR, "40");
		index.getSpecifics().put(SqlServerIndexOptions.PAD_INDEX.toString(), "ON");
		index.getSpecifics().put(SqlServerIndexOptions.ALLOW_ROW_LOCKS.toString(), "ON");
		index.getSpecifics().put(SqlServerIndexOptions.ALLOW_PAGE_LOCKS.toString(), "ON");
		index.getSpecifics().put("ONLINE", "ON");
		index.getIncludes().add("colB");
		index.toPartitioning(p->{
			p.setPartitionSchemeName("PF_FUNC1");
			p.getPartitioningColumns().add("colB");
		});
		final List<SqlOperation> list = operationFacroty.createSql(index);
		final SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		final String expected = getResource("create_index1.sql");
		assertEquals(expected, operation.getSqlText());
	}
}
