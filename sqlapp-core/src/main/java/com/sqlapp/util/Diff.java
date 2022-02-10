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

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * DIFF 
 *
 * @param <T>
 */
public class Diff<T> {
	protected final List<T> list1;

	protected final List<T> list2;

	protected final List<T> lcs=CommonUtils.list();

	protected Comparator<T> comparator;

	private final Map<Integer, T> map1 = CommonUtils.linkedMap();
	private final Map<Integer, T> map2 = CommonUtils.linkedMap();

	public Diff(List<T> list1, List<T> list2, Comparator<T> comp) {
		this.list1 = list1;
		this.list2 = list2;
		this.comparator = comp;
		initLongestCommonSubSequences(this.list1, this.list2);
	}

	public Diff(List<T> a, List<T> b) {
		this(a, b, null);
	}

	public Map<Integer, T> getLcs1() {
		return map1;
	}

	public Map<Integer, T> getLcs2() {
		return map2;
	}

	private void initLongestCommonSubSequences(List<T> list1, List<T> list2) {
		int size1 = list1.size();
		int size2 = list2.size();
		int[][] lengths = new int[size1 + 1][size2 + 1];
		for (int i = size1-1; i >= 0; i--) {
			T obj1 = list1.get(i);
			for (int j = size2-1; j >= 0; j--) {
				T obj2 = list2.get(j);
				if (eq(obj1, obj2)) {
					lengths[i][j] = lengths[i+1][j+1]+1;
				} else {
					lengths[i][j] = Math.max(lengths[i+1][j],
							lengths[i][j+1]);
				}
			}
		}
		int i = 0, j = 0;
		while(i < size1 && j < size2) {
			T obj1 = list1.get(i);
			T obj2 = list2.get(j);
			if (eq(obj1, obj2)) {
				this.lcs.add(obj1);
				map1.put(i, obj1);
				map2.put(j, obj2);
				i++;
				j++;
			} else if (lengths[i+1][j] >= lengths[i][j+1]){
				i++;
			} else{
				j++;
			}
		}
	}

	/**
	 * @return the lcs
	 */
	public List<T> getLcs() {
		return lcs;
	}

	protected boolean eq(T a, T b) {
		if (comparator != null) {
			return comparator.compare(a, b) == 0;
		}
		if (a == null) {
			if (b == null) {
				return true;
			}
			return false;
		}
		return a.equals(b);
	}

	@Override
	public String toString() {
		ToStringBuilder builder = new ToStringBuilder(this.getClass());
		builder.add("lcs1", getLcs1());
		builder.add("lcs2", getLcs2());
		return builder.toString();
	}
}
