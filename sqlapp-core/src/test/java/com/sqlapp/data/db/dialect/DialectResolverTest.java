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

package com.sqlapp.data.db.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import com.sqlapp.data.db.dialect.resolver.DefaultDialectResolver;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;

public class DialectResolverTest {

	@Test
	public void metadataIdentificationIsOptionalForExistingResolvers() {
		assertNull(new DefaultDialectResolver().resolveDatabaseMetaData(null));
	}

	@Test
	public void testCompareTo() throws XMLStreamException, IOException {
		assertEquals(1, DialectResolver.getInstance().getResolverList().size());
	}

	@Test
	public void defaultInstanceUsesContextClassLoader() {
		assertSame(DialectResolver.getInstance(),
				DialectResolver.getInstance(Thread.currentThread().getContextClassLoader()));
	}

	@Test
	public void nullClassLoaderUsesResolverClassLoader() {
		assertSame(DialectResolver.getInstance(DialectResolver.class.getClassLoader()),
				DialectResolver.getInstance(null));
	}

	@Test
	public void resolverIsCachedPerClassLoader() throws IOException {
		try (URLClassLoader first = new URLClassLoader(new URL[0], getClass().getClassLoader());
				URLClassLoader second = new URLClassLoader(new URL[0], getClass().getClassLoader())) {
			try {
				assertSame(DialectResolver.getInstance(first), DialectResolver.getInstance(first));
				assertNotSame(DialectResolver.getInstance(first), DialectResolver.getInstance(second));
			} finally {
				DialectResolver.release(first);
				DialectResolver.release(second);
			}
		}
	}

	@Test
	public void releaseDiscardsCachedResolver() throws IOException {
		try (URLClassLoader loader = new URLClassLoader(new URL[0], getClass().getClassLoader())) {
			try {
				final DialectResolver before = DialectResolver.getInstance(loader);
				DialectResolver.release(loader);
				assertNotSame(before, DialectResolver.getInstance(loader));
			} finally {
				DialectResolver.release(loader);
			}
		}
	}

}
