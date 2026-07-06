/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.data.db.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

public class SelectTableFactoryTest extends AbstractStandardFactoryTest {
	SqlFactory<Table> operationfactory;

	@BeforeEach
	public void before() {
		operationfactory = sqlFactoryRegistry.getSqlFactory(new Table(), SqlType.SELECT_FOR_APP);
	}

	@Test
	public void testGetDdlTable() {
		Table table = new Table("tableA");
		table.getColumns().add(new Column("colA").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("colB").setDataType(DataType.BIGINT).setCheck("colB>0"));
		table.getColumns().add(new Column("colC").setDataType(DataType.VARCHAR).setLength(10).setDefaultValue("'0'"));
		table.setPrimaryKey("PK_TABLEA", table.getColumns().get("colA"), table.getColumns().get("colB"));
		table.getConstraints().addUniqueConstraint("UK_tableA1", table.getColumns().get("colB"));
		table.getIndexes().add("IDX_tableA1", table.getColumns().get("colC")).getColumns().get(0).setOrder(Order.Desc);
		List<SqlOperation> list = operationfactory.createSql(table);
		SqlOperation commandText = CommonUtils.first(list);
		System.out.println(list);
		String expected = """
				SELECT /*if !_countSql */
				*
				--else count(*)
				/*end*/
				FROM "tableA"
				WHERE 1=1
					/*if isNotEmpty(colA) */
					AND "colA" IN /*colA*/(0)
					/*end*/
					/*if isNotEmpty(colA_neq) */
					AND "colA" NOT IN /*colA_neq*/(0)
					/*end*/
					/*if isNotEmpty(colA_gt) */
					AND "colA" > /*colA_gt*/0
					/*end*/
					/*if isNotEmpty(colA_lt) */
					AND "colA" < /*colA_lt*/0
					/*end*/
					/*if isNotEmpty(colA_gte) */
					AND "colA" >= /*colA_gte*/0
					/*end*/
					/*if isNotEmpty(colA_lte) */
					AND "colA" <= /*colA_lte*/0
					/*end*/
					/*if isNotEmpty(colB) */
					AND "colB" IN /*colB*/(0)
					/*end*/
					/*if isNotEmpty(colB_neq) */
					AND "colB" NOT IN /*colB_neq*/(0)
					/*end*/
					/*if isNotEmpty(colB_gt) */
					AND "colB" > /*colB_gt*/0
					/*end*/
					/*if isNotEmpty(colB_lt) */
					AND "colB" < /*colB_lt*/0
					/*end*/
					/*if isNotEmpty(colB_gte) */
					AND "colB" >= /*colB_gte*/0
					/*end*/
					/*if isNotEmpty(colB_lte) */
					AND "colB" <= /*colB_lte*/0
					/*end*/
					/*if isNotEmpty(colC) */
					AND "colC" IN /*colC*/('')
					/*end*/
					/*if isNotEmpty(colC_neq) */
					AND "colC" NOT IN /*colC_neq*/('')
					/*end*/
					/*if isNotEmpty(colC_startsWith) */
					AND "colC" LIKE /*colC_startsWith + '%'*/''
					/*end*/
					/*if isNotEmpty(colC_endsWith) */
					AND "colC" LIKE /*'%' + colC_endsWith*/''
					/*end*/
					/*if isNotEmpty(colC_contains) */
					AND "colC" LIKE /*'%' + colC_contains + '%'*/''
					/*end*/
				/*if !_countSql && isNotEmpty(_orderBy) */
				ORDER BY /*$_orderBy;sqlKeywordCheck=true*/"colA","colB"
				/*end*/
				""";
		assertEquals(expected.trim(), commandText.getSqlText().trim());
	}

}
