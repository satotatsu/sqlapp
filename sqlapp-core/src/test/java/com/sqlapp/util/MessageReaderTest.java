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

package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.UnsupportedEncodingException;
import java.util.Locale;

import org.junit.jupiter.api.Test;

public class MessageReaderTest {

	@Test
	public void testGetMessage() throws UnsupportedEncodingException {
		Locale.setDefault(Locale.ENGLISH);
		String val = MessageReader.getInstance().getMessage("E0000001");
		assertEquals(
				"Element:[{1}] was found in the required location for Element:[{0}]. lineNumber=[{2}],offset=[{3}]",
				val);
	}

}
