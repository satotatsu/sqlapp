/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.jdbc.sql.node;

import static com.sqlapp.util.CommonUtils.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.node.InputStreamNode;
import com.sqlapp.jdbc.sql.node.InputStreamNodeFactory;

public class InputStreamFactoryTest {

	@Test
	public void test1() {
		String sql="  /*@in System.in*/  ";
		InputStreamNodeFactory factory=new InputStreamNodeFactory();
		Map<Integer, InputStreamNode> map=factory.parseSql(sql);
		List<InputStreamNode> list=list(map.values());
		int i=0;
		InputStreamNode node=list.get(i++);
		Map<String, String> context=map();
		SqlParameterCollection sqlParameterCollection=new SqlParameterCollection();
		node.eval(context, sqlParameterCollection);
		assertEquals("", sqlParameterCollection.getSql());
		assertEquals(System.in, sqlParameterCollection.getInputStream());
	}

}
