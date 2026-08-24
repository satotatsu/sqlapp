/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-informix.
 */
package com.sqlapp.data.db.dialect.informix.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.sqlapp.data.schemas.ArgumentRoutine;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.jdbc.sql.ParameterDirection;

/** Utilities for SPL declarations stored in {@code sysprocbody}. */
final class InformixRoutineUtils {
	private InformixRoutineUtils() {
	}

	static void setArguments(final ArgumentRoutine<?> routine, final String definition) {
		int open = definition.indexOf('(');
		if (open < 0) {
			return;
		}
		int close = matchingParenthesis(definition, open);
		if (close < 0) {
			return;
		}
		for (String declaration : splitTopLevel(definition.substring(open + 1, close))) {
			String trimmed = declaration.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			String[] tokens = trimmed.split("\\s+");
			int nameIndex = directionToken(tokens[0]) ? 1 : 0;
			if (nameIndex >= tokens.length) {
				continue;
			}
			NamedArgument argument = new NamedArgument(tokens[nameIndex]);
			argument.setDirection(direction(tokens[0]));
			routine.getArguments().add(argument);
		}
	}

	private static int matchingParenthesis(final String text, final int open) {
		int depth = 0;
		for (int i = open; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (ch == '(') {
				depth++;
			} else if (ch == ')' && --depth == 0) {
				return i;
			}
		}
		return -1;
	}

	private static List<String> splitTopLevel(final String text) {
		List<String> result = new ArrayList<>();
		int start = 0;
		int depth = 0;
		boolean quoted = false;
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (ch == '\'' && quoted && i + 1 < text.length()
					&& text.charAt(i + 1) == '\'') {
				i++;
			} else if (ch == '\'') {
				quoted = !quoted;
			} else if (!quoted && ch == '(') {
				depth++;
			} else if (!quoted && ch == ')') {
				depth--;
			} else if (!quoted && ch == ',' && depth == 0) {
				result.add(text.substring(start, i));
				start = i + 1;
			}
		}
		result.add(text.substring(start));
		return result;
	}

	private static boolean directionToken(final String token) {
		String value = token.toUpperCase(Locale.ROOT);
		return "IN".equals(value) || "OUT".equals(value) || "INOUT".equals(value);
	}

	private static ParameterDirection direction(final String token) {
		return switch (token.toUpperCase(Locale.ROOT)) {
		case "OUT" -> ParameterDirection.Output;
		case "INOUT" -> ParameterDirection.Inout;
		default -> ParameterDirection.Input;
		};
	}
}
