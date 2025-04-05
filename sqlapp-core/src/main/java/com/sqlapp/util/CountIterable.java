package com.sqlapp.util;

import java.util.Iterator;
import java.util.function.Function;

/**
 * 指定した回数だけ値を返すIterable
 * 
 * @param <E>
 */
public class CountIterable<E> implements Iterable<E> {

	private final long start;;
	private final long limit;

	private final Function<Long, E> valueSupplier;

	public CountIterable(final long limit, final Function<Long, E> valueSupplier) {
		this(0, limit, valueSupplier);
	}

	public CountIterable(final long start, final long limit, final Function<Long, E> valueSupplier) {
		this.start = start;
		this.limit = limit;
		this.valueSupplier = valueSupplier;
	}

	@Override
	public Iterator<E> iterator() {
		return new CountIterator<E>(start, limit, valueSupplier);
	}

}
