/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-virtica.
 *
 * sqlapp-core-virtica is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-virtica is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-virtica.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.elk.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SVGTextBuilderTest {

	@Test
	void test() {
		String text = "FK_NAME\nCASCADE=(UPD,DEL)\nVIRTURL";
		String[] args = text.split("\n");
		SVGTextBuilder builder = new SVGTextBuilder(args);
		String expect = """
				FK_NAME
				<tspan dx="-4em" dy="1.2em">CASCADE=(UPD,DEL)</tspan>
				<tspan dx="-4em" dy="2.4em">VIRTURL</tspan>""";
		assertEquals(122.0, builder.getMaxLength());
		assertEquals(expect, builder.getText());
	}

}
