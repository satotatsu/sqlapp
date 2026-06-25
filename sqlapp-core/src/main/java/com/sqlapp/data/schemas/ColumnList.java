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

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.ToStringBuilder;

public class ColumnList implements List<Column>, Serializable, Cloneable {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1947995004048906450L;

	private final List<Column> list = CommonUtils.list();

	private Map<String, Column> cache = CommonUtils.map();

	@Override
	public int size() {
		return list.size();
	}

	@Override
	public boolean isEmpty() {
		return list.isEmpty();
	}

	@Override
	public Iterator<Column> iterator() {
		return list.iterator();
	}

	@Override
	public Object[] toArray() {
		return list.toArray();
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T[] toArray(T[] a) {
		return (T[]) list.toArray(new Column[a.length]);
	}

	@Override
	public boolean add(Column e) {
		put(e);
		return list.add(e);
	}

	public boolean contains(Column o) {
		return list.contains(o);
	}

	public boolean contains(String name) {
		return cache.containsKey(name);
	}

	@Override
	public boolean contains(Object o) {
		if (o instanceof Column) {
			return list.contains(o);
		} else if (o instanceof String) {
			return cache.containsKey((String) o);
		}
		throw new IllegalArgumentException("Argument type is invalid. class=" + o.getClass());
	}

	public boolean remove(Column col) {
		cache.remove(col.getName());
		return list.remove(col);
	}

	public boolean remove(String name) {
		cache.remove(name);
		return removeByString(name);
	}

	@Override
	public boolean remove(Object o) {
		if (o instanceof Column) {
			return remove((Column) o);
		} else if (o instanceof String) {
			return remove((String) o);
		}
		throw new IllegalArgumentException("Argument type is invalid. class=" + o.getClass());
	}

	private boolean removeByString(String name) {
		for (int i = 0; i < list.size(); i++) {
			if (CommonUtils.eq(list.get(i).getName(), name)) {
				list.remove(i);
				return true;
			}
		}
		return false;
	}

	public Column get(String name) {
		Column column = cache.get(name);
		if (column != null) {
			return column;
		}
		reset();
		column = cache.get(name);
		if (column != null) {
			return column;
		}
		return null;
	}

	public void reset() {
		cache.clear();
		for (Column column : list) {
			put(column);
		}
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		int i = 0;
		for (Object obj : c) {
			if (obj instanceof Column) {
				if (!list.contains(obj)) {
					return false;
				}
			} else if (obj instanceof String) {
				if (!cache.containsKey((String) obj)) {
					return false;
				}
			} else {
				throw new IllegalArgumentException(
						"Argument type is invalid. index=" + i + ", value=" + obj + " class=" + obj.getClass());
			}
			i++;
		}
		return true;
	}

	@Override
	public boolean addAll(Collection<? extends Column> c) {
		for (Column column : c) {
			if (column == null) {
				continue;
			}
			add(column);
		}
		return true;
	}

	public void addAll(Column... args) {
		for (Column column : args) {
			if (column == null) {
				continue;
			}
			add(column);
		}
	}

	public void set(Column... args) {
		this.clear();
		addAll(args);
	}

	public void set(Collection<Column> args) {
		this.clear();
		addAll(args);
	}

	@Override
	public boolean addAll(int index, Collection<? extends Column> c) {
		for (Column col : c) {
			put(col);
		}
		return list.addAll(index, c);
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		for (Object o : c) {
			if (o instanceof Column) {
				Column col = (Column) o;
				cache.remove(col.getName());
				list.remove(o);
			} else if (o instanceof String) {
				cache.remove((String) o);
				removeByString((String) o);
			} else {
				throw new IllegalArgumentException("Argument type is invalid. class=" + o.getClass());
			}
		}
		return true;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return list.retainAll(c);
	}

	@Override
	public void clear() {
		list.clear();
		cache.clear();
	}

	protected void put(Column obj) {
		if (obj == null) {
			return;
		}
		cache.put(obj.getName(), obj);
	}

	@Override
	public Column get(int index) {
		return list.get(index);
	}

	@Override
	public Column set(int index, Column element) {
		put(element);
		return list.set(index, element);
	}

	@Override
	public void add(int index, Column element) {
		put(element);
		list.add(index, element);
	}

	@Override
	public Column remove(int index) {
		Column column = list.remove(index);
		cache.remove(column.getName());
		return column;
	}

	@Override
	public int indexOf(Object o) {
		return list.indexOf(o);
	}

	@Override
	public int lastIndexOf(Object o) {
		return list.lastIndexOf(o);
	}

	@Override
	public ListIterator<Column> listIterator() {
		return list.listIterator();
	}

	@Override
	public ListIterator<Column> listIterator(int index) {
		return list.listIterator();
	}

	@Override
	public List<Column> subList(int fromIndex, int toIndex) {
		return list.subList(fromIndex, toIndex);
	}

	@Override
	public ColumnList clone() {
		final ColumnList clone = new ColumnList();
		for (Column column : this) {
			clone.add(column.clone());
		}
		return clone;
	}

	@Override
	public String toString() {
		final ToStringBuilder builder = new ToStringBuilder("columns");
		builder.addColumnNames(list);
		return builder.toString();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof ColumnList)) {
			return false;
		}
		ColumnList val = (ColumnList) obj;
		if (this.list.size() != val.size()) {
			return false;
		}
		for (int i = 0; i < this.list.size(); i++) {
			Column col1 = this.list.get(i);
			Column col2 = val.get(i);
			if (!CommonUtils.eq(col1.getName(), col2.getName())) {
				return false;
			}
			Order order1 = col1.getOrder() == Order.Asc ? null : col1.getOrder();
			Order order2 = col2.getOrder() == Order.Asc ? null : col2.getOrder();
			if (!CommonUtils.eq(order1, order2)) {
				return false;
			}
		}
		return true;
	}
}
