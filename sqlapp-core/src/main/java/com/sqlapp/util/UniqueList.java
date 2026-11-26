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

import java.io.Serializable;
import java.util.AbstractList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An ordered list of distinct elements. Bulk additions retain the first occurrence.
 * Adding an existing element has no effect. Replacing an element with a value
 * already held at another position throws {@link IllegalArgumentException}.
 * Iterators and sublists preserve the same uniqueness rules across the entire list.
 */
public class UniqueList<T> implements List<T>,Serializable,Cloneable{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;
	
	private Set<T> innerSet=CommonUtils.set();
	
	protected List<T> inner=CommonUtils.list();
	
	public UniqueList(){
	}
	
	@Override
	public boolean add(final T obj){
		if (innerSet.contains(obj)){
			return false;
		}
		final boolean ret= inner.add(obj);
		validate();
		return ret;
	}

	@Override
	public void add(final int i, final T obj){
		Objects.checkIndex(i, size() + 1);
		if (innerSet.contains(obj)){
			return;
		}
		inner.add(i, obj);
		validate();
		return;
	}

	@Override
	public boolean addAll(Collection<? extends T> args){
		return addAll(size(), args);
	}

	@Override
	public boolean addAll(final int i, Collection<? extends T> args){
		final Set<T> seen = CommonUtils.set(innerSet);
		args = args.stream().filter(seen::add).collect(Collectors.toList());
		final boolean ret = inner.addAll(i, args);
		validate();
		return ret;
	}

	protected void validate(){
		refreshMembership();
	}

	private void refreshMembership() {
		this.innerSet.clear();
		this.innerSet.addAll(this.inner);
	}

	@Override
	public int size() {
		return inner.size();
	}

	@Override
	public boolean isEmpty() {
		return inner.isEmpty();
	}

	@Override
	public boolean contains(final Object o) {
		return innerSet.contains(o);
	}

	@Override
	public Iterator<T> iterator() {
		return listIterator();
	}

	@Override
	public Object[] toArray() {
		return inner.toArray();
	}

	@Override
	public <S> S[] toArray(final S[] a) {
		return inner.toArray(a);
	}

	@Override
	public boolean remove(final Object o) {
		final boolean bool= inner.remove(o);
		if (bool){
			validate();
		}
		return bool;
	}

	@Override
	public boolean containsAll(final Collection<?> c) {
		return innerSet.containsAll(c);
	}

	@Override
	public boolean removeAll(final Collection<?> c) {
		final boolean bool=inner.removeAll(c);
		validate();
		return bool;
	}

	@Override
	public boolean retainAll(final Collection<?> c) {
		final boolean bool=inner.retainAll(c);
		validate();
		return bool;
	}

	@Override
	public void clear() {
		inner.clear();
		innerSet.clear();
	}

	@Override
	public T get(final int index) {
		return inner.get(index);
	}

	@Override
	public T set(final int index, final T element) {
		checkReplacement(inner.get(index), element);
		final T obj= inner.set(index, element);
		validate();
		return obj;
	}

	@Override
	public T remove(final int index) {
		final T obj= inner.remove(index);
		validate();
		return obj;
	}

	@Override
	public int indexOf(final Object o) {
		return inner.indexOf(o);
	}

	@Override
	public int lastIndexOf(final Object o) {
		return inner.lastIndexOf(o);
	}

	@Override
	public ListIterator<T> listIterator() {
		return listIterator(0);
	}

	@Override
	public ListIterator<T> listIterator(final int index) {
		return wrap(inner.listIterator(index));
	}

	@Override
	public List<T> subList(final int fromIndex, final int toIndex) {
		return view(inner.subList(fromIndex, toIndex));
	}
	
	@Override
	public boolean equals(final Object obj){
		return this == obj || inner.equals(obj);
	}

	@Override
	public int hashCode() {
		return inner.hashCode();
	}

	@Override
	public void sort(Comparator<? super T> comparator) {
		inner.sort(comparator);
		validate();
	}

	private void checkReplacement(T previous, T element) {
		if (!Objects.equals(previous, element) && innerSet.contains(element)) {
			throw new IllegalArgumentException("Element already exists in this list: " + element);
		}
	}

	// Views update membership without invoking validate(): subclasses may reorder
	// the backing list there, which would invalidate an iterator during its own update.
	private ListIterator<T> wrap(final ListIterator<T> iterator) {
		return new ListIterator<T>() {
			private T previous;
			private boolean canModify;

			@Override
			public boolean hasNext() {
				return iterator.hasNext();
			}

			@Override
			public boolean hasPrevious() {
				return iterator.hasPrevious();
			}

			@Override
			public int nextIndex() {
				return iterator.nextIndex();
			}

			@Override
			public int previousIndex() {
				return iterator.previousIndex();
			}

			@Override
			public T next() {
				previous = iterator.next();
				canModify = true;
				return previous;
			}

			@Override
			public T previous() {
				previous = iterator.previous();
				canModify = true;
				return previous;
			}

			@Override
			public void remove() {
				iterator.remove();
				canModify = false;
				refreshMembership();
			}

			@Override
			public void set(T element) {
				if (!canModify) {
					throw new IllegalStateException();
				}
				checkReplacement(previous, element);
				iterator.set(element);
				previous = element;
				refreshMembership();
			}

			@Override
			public void add(T element) {
				if (innerSet.contains(element)) {
					return;
				}
				iterator.add(element);
				canModify = false;
				refreshMembership();
			}
		};
	}

	private List<T> view(final List<T> backing) {
		return new AbstractList<T>() {
			@Override
			public T get(int index) {
				return backing.get(index);
			}

			@Override
			public int size() {
				return backing.size();
			}

			@Override
			public T set(int index, T element) {
				checkReplacement(backing.get(index), element);
				T previous = backing.set(index, element);
				refreshMembership();
				return previous;
			}

			@Override
			public void add(int index, T element) {
				Objects.checkIndex(index, size() + 1);
				if (innerSet.contains(element)) {
					return;
				}
				backing.add(index, element);
				refreshMembership();
			}

			@Override
			public boolean add(T element) {
				if (innerSet.contains(element)) {
					return false;
				}
				add(size(), element);
				return true;
			}

			@Override
			public boolean addAll(Collection<? extends T> elements) {
				return addAll(size(), elements);
			}

			@Override
			public boolean addAll(int index, Collection<? extends T> elements) {
				Set<T> seen = CommonUtils.set(innerSet);
				List<T> additions = elements.stream().filter(seen::add).collect(Collectors.toList());
				boolean changed = backing.addAll(index, additions);
				refreshMembership();
				return changed;
			}

			@Override
			public T remove(int index) {
				T previous = backing.remove(index);
				refreshMembership();
				return previous;
			}

			@Override
			public void sort(Comparator<? super T> comparator) {
				backing.sort(comparator);
				refreshMembership();
			}

			@Override
			public Iterator<T> iterator() {
				return listIterator();
			}

			@Override
			public ListIterator<T> listIterator(int index) {
				return wrap(backing.listIterator(index));
			}

			@Override
			public List<T> subList(int fromIndex, int toIndex) {
				return view(backing.subList(fromIndex, toIndex));
			}
		};
	}

	@Override
	public String toString(){
		final SeparatedStringBuilder builder=new SeparatedStringBuilder(",");
		builder.setStart("(").setEnd(")");
		builder.add(this);
		return builder.toString();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public UniqueList<T> clone(){
		UniqueList<T> clone;
		try {
			clone = (UniqueList<T>)super.clone();
			clone.inner=CommonUtils.list();
			clone.innerSet=CommonUtils.set();
			clone.addAll(this);
			return clone;
		} catch (final CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}
}