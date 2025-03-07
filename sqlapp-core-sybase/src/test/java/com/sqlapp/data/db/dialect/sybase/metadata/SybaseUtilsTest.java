/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sybase.
 *
 * sqlapp-core-sybase is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sybase is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sybase.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sybase.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.core.test.AbstractTest;

public class SybaseUtilsTest extends AbstractTest {

	/**
	 * プロシージャのステートメント部分抜き出しテスト1
	 */
	@Test
	public void testGetProcedureStatement1() {
		String input = getResource("create_procedure1.sql");
		String expected = getResource("create_procedure_statement1.sql");
		assertEquals(expected, SybaseUtils.getProcedureStatement(input));
	}

	/**
	 * プロシージャのステートメント部分抜き出しテスト2
	 */
	@Test
	public void testGetProcedureStatement2() {
		String input = getResource("create_procedure2.sql");
		String expected = getResource("create_procedure_statement2.sql");
		assertEquals(expected, SybaseUtils.getProcedureStatement(input));
	}

	/**
	 * プロシージャのステートメント部分抜き出しテスト3
	 */
	@Test
	public void testGetProcedureStatement3() {
		String input = getResource("create_procedure3.sql");
		String expected = getResource("create_procedure_statement3.sql");
		assertEquals(expected, SybaseUtils.getProcedureStatement(input));
	}

	/**
	 * 関数のステートメント部分抜き出しテスト1
	 */
	@Test
	public void testGetFunctionStatement1() {
		String input = getResource("create_function1.sql");
		String expected = getResource("create_function_statement1.sql");
		assertEquals(expected, SybaseUtils.getFunctionStatement(input, "FN"));
	}

	/**
	 * 関数のステートメント部分抜き出しテスト2
	 */
	@Test
	public void testGetFunctionStatement2() {
		String input = getResource("create_function2.sql");
		String expected = getResource("create_function_statement2.sql");
		assertEquals(expected, SybaseUtils.getFunctionStatement(input, "IF"));
	}

	/**
	 * 関数のステートメント部分抜き出しテスト3
	 */
	@Test
	public void testGetFunctionStatement3() {
		String input = getResource("create_function3.sql");
		String expected = getResource("create_function_statement3.sql");
		assertEquals(expected, SybaseUtils.getFunctionStatement(input, "TF"));
	}

	/**
	 * 関数のステートメント部分抜き出しテスト3(リターン変数名)
	 */
	@Test
	public void testGetFunctionStatement3_return() {
		String input = getResource("create_function3.sql");
		assertEquals("@retFindReports", SybaseUtils.getFunctionReturnName(input));
	}

	/**
	 * 関数のステートメント部分抜き出しテスト3(テーブル定義)
	 */
	@Test
	public void testGetFunctionStatement3_table() {
		String input = getResource("create_function3.sql");
		String expected = getResource("create_function_table3.sql");
		assertEquals(expected, SybaseUtils.getFunctionReturnTable(input));
	}

	/**
	 * 関数のステートメント部分抜き出しテスト5(テーブル定義)
	 */
	@Test
	public void testGetFunctionStatement5_table() {
		String input = getResource("create_function5.sql");
		String expected = getResource("create_function_table5.sql");
		assertEquals(expected, SybaseUtils.getFunctionReturnTable(input));
	}

}
