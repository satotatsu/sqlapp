/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.sqlapp.data.schemas.Sequence;

/**
 * Emits only changed options, preserving the live identity sequence position.
 */
final class OracleIdentityOptions {
	private OracleIdentityOptions() {
	}

	static List<String> changes(Sequence before, Sequence after) {
		if (Objects.equals(before, after))
			return List.of();
		if (before == null || after == null)
			throw new IllegalArgumentException("Identity sequence options require both original and target sequences");
		Sequence allowed = before.clone();
		allowed.setIncrementBy(after.getIncrementBy()).setMinValue(after.getMinValue()).setMaxValue(after.getMaxValue())
				.setCacheSize(after.getCacheSize()).setCache(after.isCache()).setCycle(after.isCycle())
				.setOrder(after.isOrder());
		if (!allowed.equals(after))
			throw new IllegalArgumentException(
					"Identity start/current value, identity sequence name and other sequence changes are not supported");
		if (after.getIncrementBy() != null && after.getIncrementBy().signum() == 0)
			throw new IllegalArgumentException("Identity increment must not be zero");
		if (after.getMinValue() != null && after.getMaxValue() != null
				&& after.getMinValue().compareTo(after.getMaxValue()) >= 0)
			throw new IllegalArgumentException("Identity minimum must be smaller than maximum");
		if (after.getCacheSize() != null && after.getCacheSize() != 0 && after.getCacheSize() < 2)
			throw new IllegalArgumentException("Identity cache size must be zero or at least two");
		List<String> result = new ArrayList<>();
		if (!Objects.equals(before.getIncrementBy(), after.getIncrementBy())) {
			if (after.getIncrementBy() == null)
				throw new IllegalArgumentException("Specify the target identity increment explicitly");
			result.add("INCREMENT BY " + after.getIncrementBy());
		}
		if (!Objects.equals(before.getMinValue(), after.getMinValue()))
			result.add(after.getMinValue() == null ? "NOMINVALUE" : "MINVALUE " + after.getMinValue());
		if (!Objects.equals(before.getMaxValue(), after.getMaxValue()))
			result.add(after.getMaxValue() == null ? "NOMAXVALUE" : "MAXVALUE " + after.getMaxValue());
		if (before.isCycle() != after.isCycle())
			result.add(after.isCycle() ? "CYCLE" : "NOCYCLE");
		if (before.isOrder() != after.isOrder())
			result.add(after.isOrder() ? "ORDER" : "NOORDER");
		if (!Objects.equals(before.getCacheSize(), after.getCacheSize()) || before.isCache() != after.isCache()) {
			if (!after.isCache() || Integer.valueOf(0).equals(after.getCacheSize()))
				result.add("NOCACHE");
			else {
				if (after.getCacheSize() == null)
					throw new IllegalArgumentException("Specify the target identity cache size explicitly");
				result.add("CACHE " + after.getCacheSize());
			}
		}
		return result;
	}
}
