package com.sqlapp.util;

import java.util.Iterator;
import java.util.function.Function;

/**
 * 指定された回数だけ繰り返すカウンター
 * 
 * @param <E>
 */
public class CountIterator<E> implements Iterator<E> {

	private long count = 0;

	private final long limit;

	private final Function<Long, E> valueSupplier;

	public CountIterator(long limit, Function<Long, E> valueSupplier) {
		this(0, limit, valueSupplier);
	}

	public CountIterator(long start, long limit, Function<Long, E> valueSupplier) {
		this.count = start;
		this.limit = limit;
		this.valueSupplier = valueSupplier;
	}

	@Override
	public boolean hasNext() {
		count++;
		return count <= limit;
	}

	@Override
	public E next() {
		return valueSupplier.apply(count - 1);
	}
}
