package com.sqlapp.data.db.dialect.mysql.db.datatype.util;

import java.util.regex.Matcher;

import com.sqlapp.data.db.datatype.util.RegexColumnTypeMatcher.MatcherColumn;
import com.sqlapp.data.db.datatype.util.TypeInformation;
import com.sqlapp.util.CommonUtils;

public class MySqlNumberMatcherColumn implements MatcherColumn {

	@Override
	public void apply(Matcher m, TypeInformation c) {
		String value = m.group("dataTypeName");
		if (value != null) {
			// c.setDataTypeName(dataTypeName[0]);
		}
		// String length = m.group("length");
		if (!CommonUtils.isEmpty(m.group("zerofill"))) {
			c.setSpecifics("zerofill", "true");
		}
		String width = m.group("width");
		if (!CommonUtils.isEmpty(width)) {
			c.setSpecifics("width", width);
		}
	}

}
