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

import org.junit.jupiter.api.Test;

public class MessageDigestsTest {

	@Test
	public void testMD2() throws UnsupportedEncodingException {
		final String val=MessageDigests.MD2.checksumAsString("abc".getBytes("UTF8")).toUpperCase();
		assertEquals("DA853B0D3F88D99B30283A69E6DED6BB", val);
	}

	@Test
	public void testMD5() throws UnsupportedEncodingException {
		final String val=MessageDigests.MD5.checksumAsString("abc".getBytes("UTF8")).toUpperCase();
		assertEquals("900150983CD24FB0D6963F7D28E17F72", val);
	}

	@Test
	public void testSHA256() throws UnsupportedEncodingException {
		final String val=MessageDigests.SHA256.checksumAsString("abc".getBytes("UTF8")).toUpperCase();
		assertEquals("BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD", val);
	}

}
