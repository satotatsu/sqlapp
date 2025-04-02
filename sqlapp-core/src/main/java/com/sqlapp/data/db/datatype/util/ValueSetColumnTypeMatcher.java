package com.sqlapp.data.db.datatype.util;

import static com.sqlapp.util.CommonUtils.trim;

import java.util.List;
import java.util.Optional;

import com.sqlapp.util.CommonUtils;

/**
 * 正規表現でValue Setを持つカラム(ENUM or SET)の型を処理します
 */
public class ValueSetColumnTypeMatcher implements ColumnTypeMatcher {

	private final String dataTypeName;

	private ColumnTypeMatcher internalColumnTypeMatcher;

	public ValueSetColumnTypeMatcher(String dataTypeName) {
		this.dataTypeName = dataTypeName;
		this.internalColumnTypeMatcher = new RegexColumnTypeMatcher(
				"(?<dataTypeName>" + dataTypeName + ")\\s*\\((?<values>.*)\\)", (m, c) -> {
					String value = m.group("dataTypeName");
					if (value != null) {
						c.setDataTypeName(value);
					}
					value = m.group("values");
					if (value != null) {
						String[] vals = value.split(",");
						List<String> set = CommonUtils.list();
						for (String val : vals) {
							set.add(trim(val));
						}
						c.setValues(set);
					}
				});
	}

	public String getDataTypeName() {
		return dataTypeName;
	}

	@Override
	public Optional<TypeInformation> match(String productDataType) {
		return internalColumnTypeMatcher.match(productDataType);
	}

}
