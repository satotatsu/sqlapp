/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.jdbc.sql;

import java.util.Arrays;
import java.util.Optional;

/** Suppressed diagnostic preserving the original SQL/business exception and its identity. */
public final class JdbcTreeDataExecutionFailure extends Exception {
	private static final long serialVersionUID = 1L;
	private final JdbcTreeDataExecutionResult result;

	JdbcTreeDataExecutionFailure(JdbcTreeDataExecutionResult result) {
		super("JdbcTreeDataSession execution failed; inspect getResult() for JDBC and commit evidence.");
		this.result = result;
	}

	public JdbcTreeDataExecutionResult getResult() { return result; }

	/** Returns the execution reports attached directly to this failure, including nested copy sessions. */
	public static java.util.List<JdbcTreeDataExecutionResult> results(Throwable failure) {
		return Arrays.stream(failure.getSuppressed())
				.filter(JdbcTreeDataExecutionFailure.class::isInstance)
				.map(JdbcTreeDataExecutionFailure.class::cast).map(JdbcTreeDataExecutionFailure::getResult).toList();
	}

	public static Optional<JdbcTreeDataExecutionResult> result(Throwable failure) {
		return results(failure).stream().findFirst();
	}
}
