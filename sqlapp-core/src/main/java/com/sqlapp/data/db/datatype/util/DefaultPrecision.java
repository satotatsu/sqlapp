package com.sqlapp.data.db.datatype.util;

import java.util.function.Supplier;

public interface DefaultPrecision {
	void setDefaultPrecision(Supplier<Integer> supplier);

	Supplier<Integer> getDefaultPrecision();

	default void setTypeInformationPrecision(TypeInformation typeInformation) {
		if (getDefaultPrecision() == null) {
			return;
		}
		Integer val = getDefaultPrecision().get();
		if (val == null) {
			return;
		}
		typeInformation.setLength(val.longValue());
	}
}
