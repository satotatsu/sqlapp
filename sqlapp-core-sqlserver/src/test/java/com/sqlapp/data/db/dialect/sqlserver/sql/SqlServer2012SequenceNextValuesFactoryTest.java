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

import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.util.CommonUtils;

public class SqlServer2012SequenceNextValuesFactoryTest extends AbstractSqlServer2022SqlFactoryTest {
	SqlFactory<Sequence> operationfactory;

	@BeforeEach
	public void before() {
		operationfactory = sqlFactoryRegistry.getSqlFactory(new Sequence(), SqlType.SEQUENCE_NEXT_VALUES);
	}

	@Test
	public void testGetDdl() {
		final Sequence obj = new Sequence("SEQA");
		obj.setCache(true);
		obj.setStartValue(10);
		obj.setIncrementBy(3);
		final List<SqlOperation> list = operationfactory.createSql(obj);
		final SqlOperation commandText = CommonUtils.first(list);
		System.out.println(list);
		final String expected = """
				WITH _N(n) AS (
					SELECT 1 AS n
					UNION ALL
					SELECT n + 1
					FROM _N
					WHERE n < /*context*/1
				)
				SELECT NEXT VALUE FOR SEQA
				FROM _N
				OPTION ( MAXRECURSION 0 )
												""";
		assertEquals(expected.trim(), commandText.getSqlText().trim());
	}
}