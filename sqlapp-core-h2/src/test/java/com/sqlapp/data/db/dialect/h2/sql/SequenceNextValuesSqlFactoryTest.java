/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.h2.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.util.CommonUtils;

/**
 * MySQL用のCreateコマンドテスト
 * 
 * @author tatsuo satoh
 * 
 */
public class SequenceNextValuesSqlFactoryTest extends AbstractH2SqlFactoryTest {
	SqlFactory<Sequence> operation;

	@BeforeEach
	public void before() {
		operation = this.sqlFactoryRegistry.getSqlFactory(new Sequence(), SqlType.SEQUENCE_NEXT_VALUES);
	}

	@Test
	public void testGetDdlTableTable1() {
		Sequence obj = new Sequence("seqa");
		List<SqlOperation> list = operation.createSql(obj);
		assertEquals(1, list.size());
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = """
				SELECT NEXT VALUE FOR [seqa]
				FROM SYSTEM_RANGE( 1, /*context*/1 )
				""";
		assertEquals(expected.trim(), operation.getSqlText().trim());
	}
}
