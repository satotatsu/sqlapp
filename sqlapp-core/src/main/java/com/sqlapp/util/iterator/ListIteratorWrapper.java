package com.sqlapp.util.iterator;

import java.util.Iterator;
import java.util.ListIterator;

public class ListIteratorWrapper<T> extends AutoCloseIterator<T> implements ListIterator<T> {

	public ListIteratorWrapper(Iterator<T> iterator) {
		super(iterator);
	}

	@Override
	public boolean hasPrevious() {
		throw new UnsupportedOperationException("hasPrevious");
	}

	@Override
	public T previous() {
		throw new UnsupportedOperationException("hasPrevious");
	}

	@Override
	public int nextIndex() {
		throw new UnsupportedOperationException("hasPrevious");
	}

	@Override
	public int previousIndex() {
		throw new UnsupportedOperationException("hasPrevious");
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException("hasPrevious");
	}

	@Override
	public void set(T e) {
		throw new UnsupportedOperationException("hasPrevious");
	}

	@Override
	public void add(T e) {
		throw new UnsupportedOperationException("hasPrevious");
	}
}
