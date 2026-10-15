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

import static org.junit.jupiter.api.Assertions.*;

import java.text.ParseException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.PartitionFunction;
import com.sqlapp.data.schemas.PartitionScheme;
import com.sqlapp.data.db.datatype.DataType;

/**
 * 
 * @author tatsuo satoh
 * 
 */
public class SqlServer2005AlterPartitionFunctionFactoryTest extends AbstractSqlServer11SqlFactoryTest {
	SqlFactory<PartitionFunction> sqlFactory;

	@BeforeEach
	public void before() {
		sqlFactory = this.sqlFactoryRegistry.getSqlFactory(new PartitionFunction(), SqlType.ALTER);
	}

	@Test
	public void test1() throws ParseException {
		PartitionFunction obj1 = getPartitionFunction1("funcA");
		PartitionFunction obj2 = getPartitionFunction2("funcA");
		List<SqlOperation> operations = sqlFactory.createDiffSql(obj1.diff(obj2));
		assertEquals(2, operations.size());
		for (int i = 0; i < operations.size(); i++) {
			SqlOperation operation = operations.get(i);
			String expected = getResource("alter_partition_function" + (i + 1) + ".sql");
			assertEquals(expected, operation.getSqlText());
		}
	}

	private PartitionFunction getPartitionFunction1(String name) throws ParseException {
		PartitionFunction obj = getPartitionFunction(name);
		obj.getValues().add("1");
		obj.getValues().add("10");
		obj.getValues().add("100");
		return obj;
	}

	private PartitionFunction getPartitionFunction2(String name) throws ParseException {
		PartitionFunction obj = getPartitionFunction(name);
		obj.getValues().add("1");
		obj.getValues().add("5");
		obj.getValues().add("10");
		return obj;
	}

	private PartitionFunction getPartitionFunction(String name) {
		PartitionFunction obj = new PartitionFunction(name);
		return obj;
	}

	@Test
	void deletionDiffUsesDropFactoryAndPreservesQuotedIdentity() {
		PartitionFunction original = new PartitionFunction("Range O'Brien]");
		var operations = sqlFactory.createDiffSql(original.diff(null));
		assertEquals(1, operations.size());
		assertEquals(SqlType.DROP, operations.get(0).getSqlType());
		assertTrue(operations.get(0).getSqlText().contains("DROP PARTITION FUNCTION [Range O'Brien]]]"),
				operations.toString());
	}

	@Test
	void dropFactoriesHonorIfExistsOnOldAndNewVersions() {
		for (int version : new int[] { 9, 10, 11, 12, 13, 14, 15, 16 }) {
			var registry = com.sqlapp.data.db.dialect.sqlserver.resolver.SqlServerDialectResolver.getInstance()
					.getDialect(version, 0).createSqlFactoryRegistry();
			registry.getOptions().setDropIfExists(true);
			String function = registry.createSql(new PartitionFunction("O'Brien"), SqlType.DROP).get(0).getSqlText();
			String scheme = registry.createSql(new PartitionScheme("O'Brien"), SqlType.DROP).get(0).getSqlText();
			assertTrue(function.contains("sys.partition_functions WHERE name = N'O''Brien'"), function);
			assertTrue(scheme.contains("sys.partition_schemes WHERE name = N'O''Brien'"), scheme);
			assertTrue(scheme.contains("DROP PARTITION SCHEME"));
			registry.getOptions().setDropIfExists(false);
			assertFalse(registry.createSql(new PartitionFunction("F"), SqlType.DROP).get(0).getSqlText()
					.contains("IF EXISTS"));
		}
	}

	@Test
	void unsupportedChangesFailEvenWhenBoundaryChangesAreAlsoRequested() throws Exception {
		PartitionFunction original = getPartitionFunction1("F");
		PartitionFunction range = original.clone().setBoundaryValueOnRight(!original.isBoundaryValueOnRight());
		range.getValues().add("200");
		assertThrows(IllegalArgumentException.class, () -> sqlFactory.createDiffSql(original.diff(range)));
		PartitionFunction renamed = original.clone().setName("G");
		assertThrows(IllegalArgumentException.class, () -> sqlFactory.createDiffSql(original.diff(renamed)));
		PartitionFunction typed = original.clone().setDataType(DataType.BIGINT);
		assertThrows(IllegalArgumentException.class, () -> sqlFactory.createDiffSql(original.diff(typed)));
		assertTrue(sqlFactory.createDiffSql(original.diff(original.clone())).isEmpty());
	}

	@Test
	void directCreateAndBoundaryPlansLeaveModelsUnchanged() throws Exception {
		PartitionFunction original = getPartitionFunction1("F").setDataType(DataType.INT);
		PartitionFunction target = original.clone();
		target.getValues().remove("100");
		target.getValues().add("200");
		String before = original.asXml(), after = target.asXml();
		assertTrue(sqlFactory.createSql(target).get(0).getSqlText().contains("CREATE PARTITION FUNCTION"));
		assertEquals(2, sqlFactory.createDiffSql(original.diff(target)).size());
		assertEquals(before, original.asXml());
		assertEquals(after, target.asXml());
	}
}
