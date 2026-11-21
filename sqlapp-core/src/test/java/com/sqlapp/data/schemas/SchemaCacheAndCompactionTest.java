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

package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.properties.ISchemaProperty;

public class SchemaCacheAndCompactionTest {

	/**
	 * The public API sizes remarks together with values, so a row with options but
	 * without remarks is set up through reflection to exercise compaction directly.
	 */
	@Test
	public void removingColumnKeepsOptionsWhenRowHasNoRemarks() throws Exception {
		final Table table = new Table("T");
		table.getColumns().add("A").add("B").add("C");
		final Row row = table.newRow();
		row.put("A", "a");
		row.put("C", "c");
		row.putOption(table.getColumns().get("C"), "opt");
		table.getRows().add(row);
		final java.lang.reflect.Field remarks = Row.class.getDeclaredField("remarks");
		remarks.setAccessible(true);
		remarks.set(row, null);

		table.getColumns().remove("B");

		final Object value = row.get("C");
		assertEquals("c", value);
		assertEquals("opt", row.getOption("C"));
		assertNull(row.getRemarks("C"));
	}

	@Test
	public void cachedSchemaPropertySetsAreReadOnly() {
		final Set<ISchemaProperty> all = SchemaUtils.getAllSchemaProperties(Table.class);
		final Set<ISchemaProperty> props = SchemaUtils.getSchemaProperties(Table.class);
		final Set<ISchemaProperty> objectProps = SchemaUtils.getSchemaObjectProperties(Schema.class);
		assertFalse(all.isEmpty());
		assertFalse(props.isEmpty());
		assertFalse(objectProps.isEmpty());
		assertThrows(UnsupportedOperationException.class, all::clear);
		assertThrows(UnsupportedOperationException.class, props::clear);
		assertThrows(UnsupportedOperationException.class, objectProps::clear);
		assertSame(all, SchemaUtils.getAllSchemaProperties(Table.class));
	}

	/**
	 * Smoke test: concurrent lookups return the same results as a sequential
	 * lookup. It cannot prove thread safety, because the caches may already be
	 * filled by other tests in the same JVM.
	 */
	@Test
	public void concurrentLookupsReturnConsistentResults() throws Exception {
		final List<Class<?>> classes = List.<Class<?>>of(Table.class, Column.class, Index.class, View.class,
				Schema.class, Catalog.class, Sequence.class, Function.class, Procedure.class, Trigger.class);
		final List<Object> expected = lookup(classes);
		final ExecutorService executor = Executors.newFixedThreadPool(8);
		try {
			final List<Future<List<Object>>> futures = new ArrayList<>();
			for (int i = 0; i < 32; i++) {
				futures.add(executor.submit((Callable<List<Object>>) () -> lookup(classes)));
			}
			for (final Future<List<Object>> future : futures) {
				assertEquals(expected, future.get());
			}
		} finally {
			executor.shutdownNow();
		}
	}

	private static List<Object> lookup(final List<Class<?>> classes) {
		final List<Object> result = new ArrayList<>();
		for (final Class<?> clazz : classes) {
			result.add(SchemaUtils.getAllSchemaProperties(clazz));
			result.add(AbstractBaseDbObject.getSimpleName(clazz));
		}
		result.add(new TableCollection().getType());
		result.add(new ColumnCollection().getType());
		result.add(AbstractBaseDbObjectCollection.getSimpleName(TableCollection.class));
		return result;
	}
}
