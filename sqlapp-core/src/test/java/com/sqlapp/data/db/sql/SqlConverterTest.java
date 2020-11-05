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
package com.sqlapp.data.db.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.SqlConverter;
import com.sqlapp.jdbc.sql.node.SqlNode;

public class SqlConverterTest {


	@Test
	public void testParseSql2() throws IOException {
		SqlConverter converter=new SqlConverter();
		converter.getExpressionConverter().setPlaceholders(true);
		converter.getExpressionConverter().setFileDirectory(new File("src/test/resources"));
		SqlNode sqlNode=converter.parseSql(new ParametersContext(), " ${readFileAsText('test.txt')}");
		assertEquals(" /*readFileAsText('test.txt')*/'1'", sqlNode.toString());
	}
	
	@Test
	public void testConvertFileData2() throws IOException {
		SqlConverter converter=new SqlConverter();
		converter.getExpressionConverter().setPlaceholders(true);
		converter.getExpressionConverter().setFileDirectory(new File("src/test/resources"));
		Object value=converter.getExpressionConverter().convert("${readFileAsText('test.txt')}", new HashMap<String,Object>());
		assertEquals("a\nb\nc", value);
	}

}
