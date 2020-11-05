/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-graphviz.
 *
 * sqlapp-graphviz is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-graphviz is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-graphviz.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.graphviz;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public abstract class AbstractElementCollection<S> extends AbstractGraphVizElement implements List<S>, Serializable,Cloneable{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1981317165486457961L;
	private List<S> list=new ArrayList<>();
	
	protected List<S> getList(){
		return list;
	}
	
	@Override
	public int size() {
		return getList().size();
	}

	@Override
	public boolean isEmpty() {
		return getList().isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return getList().contains(o);
	}

	@Override
	public Iterator<S> iterator() {
		return getList().iterator();
	}

	@Override
	public Object[] toArray() {
		return getList().toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return getList().toArray(a);
	}

	@Override
	public boolean add(S e) {
		boolean bool= getList().add(e);
		if (bool){
			renew();
		}
		return bool;
	}

	@Override
	public boolean remove(Object o) {
		boolean bool= getList().remove(o);
		if (bool){
			renew();
		}
		return bool;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return getList().containsAll(c);
	}

	@Override
	public boolean addAll(Collection<? extends S> c) {
		boolean bool= getList().addAll(c);
		renew();
		return bool;
	}

	@Override
	public boolean addAll(int index, Collection<? extends S> c) {
		boolean bool= getList().addAll(index, c);
		renew();
		return bool;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		boolean bool= getList().removeAll(c);
		renew();
		return bool;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return getList().retainAll(c);
	}

	@Override
	public void clear() {
		getList().clear();
		renew();
	}

	@Override
	public S get(int index) {
		return getList().get(index);
	}

	@Override
	public S set(int index, S element) {
		S S= getList().set(index, element);
		renew();
		return S;
	}

	@Override
	public void add(int index, S element) {
		getList().add(index, element);
		renew();
	}

	@Override
	public S remove(int index) {
		S S= getList().remove(index);
		renew();
		return S;
	}
	
	protected void renew(){
		
	}

	@Override
	public int indexOf(Object o) {
		return getList().indexOf(o);
	}

	@Override
	public int lastIndexOf(Object o) {
		return getList().lastIndexOf(o);
	}

	@Override
	public ListIterator<S> listIterator() {
		return getList().listIterator();
	}

	@Override
	public ListIterator<S> listIterator(int index) {
		return getList().listIterator();
	}

	@Override
	public List<S> subList(int fromIndex, int toIndex) {
		return getList().subList(fromIndex, toIndex);
	}
	
	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public AbstractElementCollection<S> clone(){
		AbstractElementCollection clone;
		try {
			clone = (AbstractElementCollection) super.clone();
			clone.list=new ArrayList<>();
			clone.addAll(this);
			clone.renew();
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public String toString(){
		StringBuilder builder=new StringBuilder();
		for(S val:this){
			builder.append(val);
			builder.append("\n");
		}
		return builder.substring(0, builder.length()-1);
	}

}
