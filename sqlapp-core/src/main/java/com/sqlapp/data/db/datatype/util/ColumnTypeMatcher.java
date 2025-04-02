package com.sqlapp.data.db.datatype.util;

import java.util.Optional;

/***
 * カラムの型一致判定インタフェース
 */
@FunctionalInterface
public interface ColumnTypeMatcher {

	Optional<TypeInformation> match(String productDataType);
}
