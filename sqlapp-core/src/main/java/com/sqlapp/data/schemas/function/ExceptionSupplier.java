package com.sqlapp.data.schemas.function;

@FunctionalInterface
public interface ExceptionSupplier<T> {
	/**
	 * Gets a result.
	 *
	 * @return a result
	 */
	T get() throws Exception;
}