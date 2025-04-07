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
import java.util.function.Function;

/**
 * SQL Exceptionをthrowsに持つFunction
 */
@FunctionalInterface
public interface SQLFunction<T, R> {

	/**
	 * Applies this function to the given argument.
	 *
	 * @param t the function argument
	 * @return the function result
	 */
	R apply(T t) throws SQLException;

	/**
	 *
	 * @param <V>    the type of input to the {@code before} SQLFunction, and to the
	 *               composed function
	 * @param before the function to apply before this function is applied
	 * @return a composed function that first applies the {@code before} function
	 *         and then applies this function
	 * @throws NullPointerException if before is null
	 *
	 * @see #andThen(SQLFunction)
	 */
	default <V> SQLFunction<V, R> compose(SQLFunction<? super V, ? extends T> before) {
		Objects.requireNonNull(before);
		return (V v) -> apply(before.apply(v));
	}

	/**
	 *
	 * @param <V>    the type of input to the {@code before} SQLFunction, and to the
	 *               composed function
	 * @param before the function to apply before this function is applied
	 * @return a composed function that first applies the {@code before} function
	 *         and then applies this function
	 * @throws NullPointerException if before is null
	 *
	 * @see #andThen(SQLFunction)
	 */
	default <V> SQLFunction<V, R> compose(Function<? super V, ? extends T> before) {
		Objects.requireNonNull(before);
		return (V v) -> apply(before.apply(v));
	}

	/**
	 *
	 * @param <V>   the type of output of the {@code after} SQLFunction, and of the
	 *              composed function
	 * @param after the function to apply after this function is applied
	 * @return a composed function that first applies this function and then applies
	 *         the {@code after} function
	 * @throws NullPointerException if after is null
	 *
	 * @see #compose(Function)
	 */
	default <V> SQLFunction<T, V> andThen(SQLFunction<? super R, ? extends V> after) {
		Objects.requireNonNull(after);
		return (T t) -> after.apply(apply(t));
	}

	/**
	 *
	 * @param <V>   the type of output of the {@code after} SQLFunction, and of the
	 *              composed function
	 * @param after the function to apply after this function is applied
	 * @return a composed function that first applies this function and then applies
	 *         the {@code after} function
	 * @throws NullPointerException if after is null
	 *
	 * @see #compose(Function)
	 */
	default <V> SQLFunction<T, V> andThen(Function<? super R, ? extends V> after) {
		Objects.requireNonNull(after);
		return (T t) -> after.apply(apply(t));
	}

	/**
	 * Returns a SQLFunction that always returns its input argument.
	 *
	 * @param <T> the type of the input and output objects to the function
	 * @return a function that always returns its input argument
	 */
	static <T> SQLFunction<T, T> identity() {
		return t -> t;
	}
}
