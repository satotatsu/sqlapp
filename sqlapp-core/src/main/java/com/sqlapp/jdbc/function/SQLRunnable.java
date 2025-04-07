/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.jdbc.function;

import java.sql.SQLException;
import java.util.Objects;

/**
 * SQL Exceptionをthrowsに持つFunction
 */
@FunctionalInterface
public interface SQLRunnable {
	void run() throws SQLException;

	/**
	 *
	 * @param after the operation to perform after this operation
	 * @return a composed {@code Consumer} that performs in sequence this operation
	 */
	default SQLRunnable andThen(SQLRunnable after) {
		Objects.requireNonNull(after);
		return () -> {
			run();
			after.run();
		};
	}

	/**
	 *
	 * @param after the operation to perform after this operation
	 * @return a composed {@code Consumer} that performs in sequence this operation
	 */
	default SQLRunnable andThen(Runnable after) {
		Objects.requireNonNull(after);
		return () -> {
			run();
			after.run();
		};
	}
}
