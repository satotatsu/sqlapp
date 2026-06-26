/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import com.sqlapp.util.CommonUtils;

import lombok.Setter;

@Setter
public class SchemaTypeHandler {
	private Consumer<Catalog> catalogConsumer;
	private Consumer<List<Schema>> schemasConsumer;
	private Consumer<Schema> schemaConsumer;
	private Consumer<List<Table>> tablesConsumer;
	private Consumer<Table> tableConsumer;

	@SuppressWarnings("unchecked")
	public void apply(Object obj) {
		if (obj instanceof Catalog) {
			if (catalogConsumer != null) {
				catalogConsumer.accept((Catalog) obj);
			}
			return;
		}
		if (obj instanceof Schema) {
			if (catalogConsumer != null) {
				schemaConsumer.accept((Schema) obj);
			}
			return;
		}
		if (obj instanceof Table) {
			if (tableConsumer != null) {
				tableConsumer.accept((Table) obj);
			}
			return;
		}
		if (obj instanceof Collection) {
			Object val = CommonUtils.first((Collection<?>) obj);
			if (val instanceof Table) {
				if (tablesConsumer != null) {
					tablesConsumer.accept((List<Table>) obj);
				}
				return;
			}
			if (val instanceof Schema) {
				if (schemasConsumer != null) {
					schemasConsumer.accept((List<Schema>) obj);
				}
				return;
			}
		}
	}
}
