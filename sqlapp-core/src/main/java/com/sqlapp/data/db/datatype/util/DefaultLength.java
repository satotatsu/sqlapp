package com.sqlapp.data.db.datatype.util;

import java.util.function.Supplier;

public interface DefaultLength {
	void setDefaultLength(Supplier<Long> supplier);

	Supplier<Long> getDefaultLength();

	default void setTypeInformationLength(TypeInformation typeInformation) {
		if (getDefaultLength() == null) {
			return;
		}
		Long val = getDefaultLength().get();
		if (val == null) {
			return;
		}
		typeInformation.setLength(val);
	}
}
