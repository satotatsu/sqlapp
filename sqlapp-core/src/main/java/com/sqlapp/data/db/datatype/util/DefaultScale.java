package com.sqlapp.data.db.datatype.util;

import java.util.function.Supplier;

public interface DefaultScale {
	void setDefaultScale(Supplier<Integer> supplier);

	Supplier<Integer> getDefaultScale();

	default void setTypeInformationScale(TypeInformation typeInformation) {
		if (getDefaultScale() == null) {
			return;
		}
		Integer val = getDefaultScale().get();
		if (val == null) {
			return;
		}
		typeInformation.setScale(val);
	}
}
