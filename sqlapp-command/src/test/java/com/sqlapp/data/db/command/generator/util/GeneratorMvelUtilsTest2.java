package com.sqlapp.data.db.command.generator.util;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.iterable.CombinedIterable;
import com.sqlapp.util.iterator.Iterators;

class GeneratorMvelUtilsTest2 {

	@Test
	void test() {
		final Iterable<Integer> iterable1 = create(5);
		final Iterable<Integer> iterable2 = create(4);
		final Iterable<Integer> iterable3 = create(0);
		final Iterable<Integer> iterable4 = create(3);
		CombinedIterable<Integer> combinedIterable = new CombinedIterable<>(
				List.of(iterable1, iterable2, iterable3, iterable4));
		for (Integer val : combinedIterable) {
			System.out.println(val);
		}
	}

	private Iterable<Integer> create(int size) {
		return Iterators.range(size);
	}

}
