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

package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Dimension;
import com.sqlapp.data.schemas.DimensionLevel;
import com.sqlapp.data.schemas.DimensionLevelColumn;
import com.sqlapp.util.CommonUtils;

public class OracleCreateDimensionTest extends AbstractOracleSqlFactoryTest {
	SqlFactory<Dimension> createOperationFactory;
	SqlFactory<Dimension> dropOperationFactory;

	@BeforeEach
	public void before() {
		createOperationFactory = sqlFactoryRegistry.getSqlFactory(
				new Dimension("dim"), SqlType.CREATE);
		dropOperationFactory = sqlFactoryRegistry.getSqlFactory(
				new Dimension("dim"), SqlType.DROP);
	}

	@Test
	public void testCreateTest1() {
		Dimension obj = getDimension("customers_dim");
		List<SqlOperation> list = createOperationFactory.createSql(obj);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("create_dimension1.sql");
		//assertEquals(expected, operation.getSqlText());
	}

	private Dimension getDimension(String name) {
		Dimension obj = new Dimension(name);
		DimensionLevel level = obj.newLevel("customer");
		DimensionLevelColumn column = level.newColumn();
		column.setName("customers.cust_id");
		level.getColumns().add(column);
		obj.getLevels().add(level);
		//
		level = obj.newLevel("city");
		column = level.newColumn();
		column.setName("customers.cust_city");
		level.getColumns().add(column);
		obj.getLevels().add(level);
		//
		level = obj.newLevel("state");
		column = level.newColumn();
		column.setName("customers.cust_state_province");
		level.getColumns().add(column);
		obj.getLevels().add(level);
		//
		level = obj.newLevel("country");
		column = level.newColumn();
		column.setName("customers.country_id");
		level.getColumns().add(column);
		obj.getLevels().add(level);
		//
		level = obj.newLevel("subregion");
		column = level.newColumn();
		column.setName("customers.country_subregion");
		level.getColumns().add(column);
		obj.getLevels().add(level);
		//
		level = obj.newLevel("region");
		column = level.newColumn();
		column.setName("customers.country_region");
		level.getColumns().add(column);
		obj.getLevels().add(level);
		return obj;
	}

	@Test
	public void testDropTest1() {
		Dimension obj = getDimension("customers_dim");
		List<SqlOperation> list = dropOperationFactory.createSql(obj);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("drop_dimension1.sql");
		assertEquals(expected, operation.getSqlText());
	}

}
