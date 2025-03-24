package com.sqlapp.gradle.plugins.extension;

import org.gradle.api.Action;
import org.gradle.api.tasks.Internal;

import com.sqlapp.data.db.sql.Options;
import com.sqlapp.data.db.sql.TableOptions;

public abstract class OptionsExtension extends Options {

	@Internal
	public void call(Action<OptionsExtension> cons) {
		cons.execute(this);
	}

	public void tableOptions(Action<? super TableOptions> action) {
		action.execute(getTableOptions());
	}
}
