package com.sqlapp.data.db.dialect.mysql.db.datatype.util;

import java.util.Optional;

import com.sqlapp.data.db.datatype.util.ColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.RegexColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.TypeInformation;

/**
 * MySQLでのUnsigned数値型カラムの型を処理します
 */
public class MysqlUnsignedNumberColumnTypeMatcher implements ColumnTypeMatcher {

	private final String dataTypeName;

	private ColumnTypeMatcher internalColumnTypeMatcher;

	public MysqlUnsignedNumberColumnTypeMatcher(String... dataTypeName) {
		this.dataTypeName = dataTypeName[0];
		this.internalColumnTypeMatcher = new RegexColumnTypeMatcher(
				MySqlColumnTypeMatcherUtils.joinForNumber(dataTypeName)
						+ "\\s*(\\(((?<length>\\s*[0-9]+)\\s*)\\))?(?<unsigned>\\s+unsigned)(?<zerofill>\\s+zerofill)?",
				new MySqlNumberMatcherColumn());
	}

	public String getDataTypeName() {
		return dataTypeName;
	}

	@Override
	public Optional<TypeInformation> match(String productDataType) {
		return internalColumnTypeMatcher.match(productDataType);
	}

}
