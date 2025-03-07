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

package com.sqlapp.util.eval.mvel;


import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.util.FileUtils;

public class MvelUtilsTest {

	@BeforeEach
	public void setUp() throws Exception {
	}

	@Test
	public void testParseBean() throws ParseException, URISyntaxException, IOException{
		MvelUtils.setBasePath("src/test/resources");
		String path=MvelUtils.writeZip("com/sqlapp/data/schemas", "schemas.zip", "MS932");
		FileUtils.remove("src/test/resources/schemas.zip");
	}


}
