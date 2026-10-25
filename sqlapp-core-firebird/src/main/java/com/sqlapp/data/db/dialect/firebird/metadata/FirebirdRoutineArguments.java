/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import java.sql.Connection;
import java.util.*;
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ArgumentRoutine;
import com.sqlapp.data.schemas.NamedArgument;

/** Bounds catalog predicates and groups arguments without scanning unrelated routines. */
final class FirebirdRoutineArguments {
	static Map<String, List<NamedArgument>> read(Connection connection, ParametersContext context,
			List<? extends ArgumentRoutine<?>> routines, RoutineArgumentReader<?> reader) {
		Map<String, List<NamedArgument>> result = new LinkedHashMap<>();
		List<String> names = routines.stream().map(routine -> routine.getName()).distinct().toList();
		for (int offset = 0; offset < names.size(); offset += 500) {
			ParametersContext batch = context.clone();
			batch.put("routineName", names.subList(offset, Math.min(offset + 500, names.size())));
			for (NamedArgument argument : reader.getAllFull(connection, batch))
				result.computeIfAbsent(argument.getRoutineName(), key -> new ArrayList<>()).add(argument);
		}
		return result;
	}
	private FirebirdRoutineArguments() { }
}
