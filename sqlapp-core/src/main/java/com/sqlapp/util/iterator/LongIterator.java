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

package com.sqlapp.util.iterator;

import java.io.Serializable;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.sqlapp.util.ToStringBuilder;

/**
 * For文の代わりに使用するIterator
 * 
 * @author satoh
 *
 */
class LongIterator implements Iterator<Long>, Serializable, Cloneable {

	private static final long serialVersionUID = 1L;

	private final long start;
	private final long end;
	private final long step;

	private long current;

	public LongIterator(long end) {
		this(0, end, 1);
	}

	public LongIterator(long start, long end) {
		this(start, end, 1);
	}

	public LongIterator(long start, long end, long step) {
		if (step == 0) {
			throw new IllegalArgumentException("step must not be 0.");
		}
		if (start < end && step < 0) {
			throw new IllegalArgumentException("step must be positive.");
		}
		if (start > end && step > 0) {
			throw new IllegalArgumentException("step must be negative.");
		}

		this.start = start;
		this.end = end;
		this.step = step;
		this.current = start;
	}

	@Override
	public boolean hasNext() {
		if (step > 0) {
			return current < end;
		}
		return current > end;
	}

	@Override
	public Long next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		long value = current;
		current += step;
		return value;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	@Override
	public LongIterator clone() {
		LongIterator clone = new LongIterator(start, end, step);
		clone.current = current;
		return clone;
	}

	@Override
	public String toString() {
		ToStringBuilder builder = new ToStringBuilder(IntegerIterator.class);
		builder.add("start", start);
		builder.add("end", end);
		builder.add("step", step);
		builder.add("current", current);
		return builder.toString();
	}
}
