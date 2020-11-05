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
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ProductVersionInfoTest {

	ProductVersionInfo productVersionInfo1;

	ProductVersionInfo productVersionInfo2;

	private int majorVersion = 10;

	private int minorVersion = 20;

	private int revision = 30;

	@BeforeEach
	public void setup() {
		productVersionInfo1 = new ProductVersionInfo();
		productVersionInfo1.setMajorVersion(majorVersion);
		productVersionInfo1.setMinorVersion(minorVersion);
		productVersionInfo1.setRevision(revision);
		//
		productVersionInfo2 = new ProductVersionInfo();
		productVersionInfo2.setMajorVersion(majorVersion);
		productVersionInfo2.setMinorVersion(minorVersion);
	}

	@Test
	public void testEq() {
		assertTrue(productVersionInfo1.eq(majorVersion, minorVersion,
				revision));
		assertTrue(productVersionInfo2.eq(majorVersion, minorVersion, null));
	}

	@Test
	public void testGte() {
		assertTrue(productVersionInfo1.gte(majorVersion, minorVersion,
				revision));
		assertTrue(productVersionInfo2.gte(majorVersion, minorVersion, null));
		//
		assertTrue(productVersionInfo1.gte(majorVersion - 1, minorVersion,
				revision));
		assertTrue(productVersionInfo2
				.gte(majorVersion - 1, minorVersion, null));
		//
		assertTrue(productVersionInfo1.gte(majorVersion, minorVersion - 1,
				revision));
		assertTrue(productVersionInfo2
				.gte(majorVersion, minorVersion - 1, null));
		//
		assertTrue(productVersionInfo1.gte(majorVersion, minorVersion,
				revision - 1));
		//
		assertTrue(productVersionInfo1.gte(majorVersion - 1, minorVersion - 1,
				revision - 1));
	}

	@Test
	public void testGt() {
		assertFalse(productVersionInfo1.gt(majorVersion, minorVersion,
				revision));
		//
		assertTrue(productVersionInfo1.gt(majorVersion - 1, minorVersion,
				revision));
		assertTrue(productVersionInfo1.gt(majorVersion, minorVersion - 1,
				revision));
		assertTrue(productVersionInfo1.gt(majorVersion, minorVersion,
				revision - 1));
		assertTrue(productVersionInfo1.gt(majorVersion - 1, minorVersion - 1,
				revision - 1));
		//
		assertTrue(productVersionInfo1.gt(majorVersion, minorVersion, null));
	}

	@Test
	public void testLt() {
		assertFalse(productVersionInfo1.lt(majorVersion, minorVersion,
				revision));
		//
		assertTrue(productVersionInfo1.lt(majorVersion + 1, minorVersion,
				revision));
		assertTrue(productVersionInfo1.lt(majorVersion, minorVersion + 1,
				revision));
		assertTrue(productVersionInfo1.lt(majorVersion, minorVersion,
				revision + 1));
		assertTrue(productVersionInfo1.lt(majorVersion + 1, minorVersion + 1,
				revision + 1));
		//
		assertFalse(productVersionInfo1.lt(majorVersion, minorVersion, null));
	}

	@Test
	public void testLte() {
		assertTrue(productVersionInfo1.lte(majorVersion, minorVersion,
				revision));
		assertTrue(productVersionInfo2.lte(majorVersion, minorVersion, null));
		//
		assertTrue(productVersionInfo1.lte(majorVersion + 1, minorVersion,
				revision));
		assertTrue(productVersionInfo2
				.lte(majorVersion + 1, minorVersion, null));
		//
		assertTrue(productVersionInfo1.lte(majorVersion, minorVersion + 1,
				revision));
		assertTrue(productVersionInfo2
				.lte(majorVersion, minorVersion + 1, null));
		//
		assertTrue(productVersionInfo1.lte(majorVersion, minorVersion,
				revision + 1));
		//
		assertTrue(productVersionInfo1.lte(majorVersion + 1, minorVersion + 1,
				revision + 1));
	}

}
