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
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.stream.Collectors;

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
		if (innerSet.contains(obj)){
			return;
		}
		inner.add(i, obj);
		validate();
		return;
	}

	@Override
	public boolean addAll(Collection<? extends T> args){
		args=args.stream().filter(a->!innerSet.contains(a)).collect(Collectors.toList());
		for(final T arg:args){
			if (innerSet.contains(arg)){
				continue;
			}
		}
		final boolean ret= inner.addAll(args);
		validate();
		return ret;
	}

	@Override
	public boolean addAll(final int i, Collection<? extends T> args){
		args=args.stream().filter(a->!innerSet.contains(a)).collect(Collectors.toList());
		for(final T arg:args){
			if (innerSet.contains(arg)){
				continue;
			}
		}
		final boolean ret= inner.addAll(i, args);
		validate();
		return ret;
	}

	protected void validate(){
		this.innerSet.clear();
		for(final T arg:this){
			this.innerSet.add(arg);
		}
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
		return inner.iterator();
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
		return inner.listIterator();
	}

	@Override
	public ListIterator<T> listIterator(final int index) {
		return inner.listIterator();
	}

	@Override
	public List<T> subList(final int fromIndex, final int toIndex) {
		return inner.subList(fromIndex, toIndex);
	}
	
	@Override
	public boolean equals(final Object obj){
		if (obj==null){
			return false;
		}
		if (this==obj){
			return true;
		}
		if (!(this instanceof UniqueList)){
			return false;
		}
		final UniqueList<?> cst=(UniqueList<?>)obj;
		if (!CommonUtils.eq(this.inner, cst.inner)){
			return false;
		}
		return true;
	}
	
	@Override
	public int hashCode() {
		final HashCodeBuilder builder=new HashCodeBuilder();
		builder.append(this.inner);
		builder.append(this.innerSet);
		return builder.hashCode();
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