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
package com.sqlapp.util;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import java.util.function.BiConsumer;

public class LinkedProperties extends Properties {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private Set<Object> keys = CommonUtils.linkedSet();

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public synchronized Enumeration<Object> keys() {
		Vector keyList = new Vector();
		for (Object key : keys) {
			keyList.add(key);
		}
		return keyList.elements();
	}

	@Override
	public synchronized Collection<Object> values() {
		List<Object> result = CommonUtils.list();
		for (Object key : keys) {
			result.add(this.get(key));
		}
		return result;
	}

	@Override
	public Set<Map.Entry<Object, Object>> entrySet() {
		Map<Object, Object> map = new LinkedHashMap<>();
		Enumeration<Object> keysEnum = keys();
		while (keysEnum.hasMoreElements()) {
			Object key = keysEnum.nextElement();
			map.put(key, this.get(key));
		}
		return map.entrySet();
	}

	@Override
	public Set<Object> keySet() {
		return CommonUtils.linkedSet(this.keys);
	}

	@Override
	public synchronized Enumeration<Object> elements() {
		Vector<Object> list = new Vector<Object>();
		for (Object key : keys) {
			list.add(this.get(key));
		}
		return list.elements();
	}

	@Override
	public synchronized Object setProperty(String key, String value) {
		keys.add(key);
		return super.put(key, value);
	}

	@Override
	public Enumeration<?> propertyNames() {
		return keys();
	}

	@Override
	public Set<String> stringPropertyNames() {
		Set<String> result = CommonUtils.linkedSet();
		for (Object key : keys) {
			result.add(key.toString());
		}
		return result;
	}

	@Override
	public synchronized Object put(Object key, Object value) {
		keys.add(key);
		return super.put(key, value);
	}

	@Override
	public synchronized Object remove(Object key) {
		keys.remove(key);
		return super.remove(key);
	}

	@Override
	public synchronized Object putIfAbsent(Object key, Object value) {
		keys.remove(key);
		return super.putIfAbsent(key, value);
	}

	@Override
	public void list(PrintStream out) {
		out.println("-- listing properties --");
		for (Object key : keys) {
			Object obj = get(key);
			String val = obj.toString();
			if (val.length() > 40) {
				val = val.substring(0, 37) + "...";
			}
			out.println(key + "=" + val);
		}
	}

	@Override
	public void list(PrintWriter out) {
		out.println("-- listing properties --");
		for (Object key : keys) {
			Object obj = get(key);
			String val = obj.toString();
			if (val.length() > 40) {
				val = val.substring(0, 37) + "...";
			}
			out.println(key + "=" + val);
		}
	}

	@Override
	public synchronized void forEach(BiConsumer<Object, Object> action) {
		Objects.requireNonNull(action);
		Set<Map.Entry<Object, Object>> entries=entrySet();
		for (Map.Entry<Object, Object> entry : entries) {
			if (entry != null) {
				action.accept(entry.getKey(), entry.getValue());
			}
		}
	}

}
