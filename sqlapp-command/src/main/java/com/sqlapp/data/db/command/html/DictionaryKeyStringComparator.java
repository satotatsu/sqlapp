package com.sqlapp.data.db.command.html;

import java.util.Comparator;
import java.util.Map;

import com.sqlapp.util.CommonUtils;

public class DictionaryKeyStringComparator implements Comparator<String> {

	private final Map<String, Integer> keywordsMap;

	public DictionaryKeyStringComparator() {
		this.keywordsMap = CommonUtils.linkedMap();
	}

	public DictionaryKeyStringComparator(Map<String, Integer> keywordsMap) {
		this.keywordsMap = keywordsMap;
	}

	@Override
	public int compare(String o1, String o2) {
		final String[] split1 = o1.split("\\.");
		final String[] split2 = o2.split("\\.");
		if (split1.length > split2.length) {
			return 1;
		} else if (split1.length < split2.length) {
			return -1;
		}
		int comp = compareWithoutLast(split1, split2);
		if (comp != 0) {
			return comp;
		}
		return compareLast(split1, split2);
	}

	private int compareWithoutLast(String[] split1, String[] split2) {
		for (int i = 0; i < split1.length - 1; i++) {
			int comp = split1[i].compareTo(split2[i]);
			if (comp != 0) {
				return comp;
			}
		}
		return 0;
	}

	private int compareLast(String[] split1, String[] split2) {
		String value1 = split1[split1.length - 1];
		String value2 = split2[split2.length - 1];
		Integer int1 = keywordsMap.get(value1);
		Integer int2 = keywordsMap.get(value2);
		if (int1 == null) {
			if (int2 == null) {
				return value1.compareTo(value2);
			} else {
				return 1;
			}
		} else {
			if (int2 == null) {
				return 0;
			} else {
				return int1.compareTo(int2);
			}
		}
	}
}
