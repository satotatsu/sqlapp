/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.util;

import java.util.*;
import com.sqlapp.data.schemas.Trigger;

/** Retains native activation order without inventing FOLLOWS dependencies. */
public final class MySqlTriggerOrder {
	public static final String ACTION_ORDER = "mysql_action_order";
	private MySqlTriggerOrder() { }

	public static Integer order(Trigger trigger) {
		String value = trigger.getSpecifics().get(ACTION_ORDER);
		if (value == null) return null;
		try {
			if (!value.matches("[0-9]+")) throw new NumberFormatException();
			return Integer.valueOf(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("mysql_action_order must be a nonnegative integer: " + value, e);
		}
	}

	public static List<Trigger> sort(List<Trigger> triggers) {
		Map<List<String>, List<Trigger>> groups = new LinkedHashMap<>();
		for (Trigger trigger : triggers) {
			String schema = trigger.getSchemaName() == null ? trigger.getTableSchemaName() : trigger.getSchemaName();
			var key = Arrays.asList(trigger.getCatalogName(), schema, trigger.getTableName(),
					upper(trigger.getActionTiming()), String.join(",", trigger.getEventManipulation()));
			groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(trigger);
		}
		List<Trigger> result = new ArrayList<>();
		for (List<Trigger> group : groups.values()) {
			boolean ordered = group.stream().anyMatch(trigger -> order(trigger) != null);
			if (ordered && group.size() > 1) {
				Set<Integer> seen = new HashSet<>();
				for (Trigger trigger : group) {
					Integer rank = order(trigger);
					if (rank == null || rank == 0 || !seen.add(rank)) {
						throw new IllegalArgumentException("All triggers in an ordered table/event/timing group require distinct positive mysql_action_order values");
					}
				}
				group.sort(Comparator.comparingInt(trigger -> order(trigger)));
			}
			result.addAll(group);
		}
		return result;
	}
	private static String upper(String value) { return value == null ? null : value.toUpperCase(Locale.ROOT); }
}
