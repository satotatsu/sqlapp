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

package com.sqlapp.data.schemas.rowiterator;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.data.schemas.function.RowValueConverter;
import com.sqlapp.util.JsonConverter;
import com.sqlapp.util.TomlConverter;

/**
 * TOMLの行のIterator
 * 
 * @author tatsuo satoh
 *
 */
public class TomlRowIteratorHandler extends JsonRowIteratorHandler {

	public TomlRowIteratorHandler(File file, TomlConverter jsonConverter, RowValueConverter valueConverter) {
		super(file, jsonConverter, valueConverter);
	}

	public TomlRowIteratorHandler(File file, TomlConverter jsonConverter) {
		super(file, jsonConverter);
	}

	public TomlRowIteratorHandler(File file) {
		super(file, new TomlConverter());
	}

	@Override
	public Iterator<Row> iterator(final RowCollection c) {
		return new TomlRowIterator(c, this.getFile(), this.getJsonConverter(), 0L, this.getRowValueConverter());
	}

	public static class TomlRowIterator extends JsonRowIterator {
		public TomlRowIterator(final RowCollection c, final File file, final JsonConverter jsonConverter,
				final long index, final RowValueConverter valueConverter) {
			super(c, file, jsonConverter, index, valueConverter);
		}

		@SuppressWarnings("unchecked")
		@Override
		protected void preInitialize() throws Exception {
			Object obj = this.getJsonConverter().fromJsonString(this.getFile(), Object.class);
			Object items = null;
			if (obj instanceof Map) {
				Map<String, Object> map = (Map<String, Object>) obj;
				if (map.containsKey("items")) {
					items = map.get("items");
				} else {
					throw new IllegalArgumentException("File is not TOML format.file=" + getFile().getAbsolutePath());
				}
			}
			if (items instanceof List) {
				List<Map<String, Object>> list = (List<Map<String, Object>>) items;
				this.setList(list);
				this.setIterator(list.iterator());
			} else {
				throw new IllegalArgumentException("File is not TOML format.file=" + getFile().getAbsolutePath());
			}
		}
	}
}
