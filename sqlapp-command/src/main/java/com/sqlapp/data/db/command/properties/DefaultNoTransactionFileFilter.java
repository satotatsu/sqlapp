package com.sqlapp.data.db.command.properties;

import java.io.File;
import java.util.function.Predicate;

import com.sqlapp.util.FileUtils;

public class DefaultNoTransactionFileFilter implements Predicate<File> {

	@Override
	public boolean test(File t) {
		String name = FileUtils.getFileNameWithoutExtension(t);
		if (name.endsWith("NoTran")) {
			return true;
		}
		if (name.endsWith("NoTransaction")) {
			return true;
		}
		name = name.toUpperCase();
		if (name.endsWith("_NO_TRAN")) {
			return true;
		}
		if (name.endsWith("_NO_TRANSACTION")) {
			return true;
		}
		if (name.endsWith("_NOTRAN")) {
			return true;
		}
		return false;
	}

}
