package com.sqlapp.data.db.command.generator.setting.strategy;

import java.util.Map;
import java.util.Optional;

/**
 * 
 */
@FunctionalInterface
public interface ValueSelectionFunction {

	/**
	 * Applies this function to the given arguments.
	 *
	 * @param i index
	 * @return the function result
	 */
	Optional<Map<String, Object>> get(int i);

}
