package com.sqlapp.gradle.plugins.extension;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.Placeholders;

public interface PlaceholderInject {

	@Input
	@Optional
	Property<String> getPlaceholderPrefix();

	@Input
	@Optional
	Property<String> getPlaceholderSuffix();

	@Input
	@Optional
	Property<Boolean> getPlaceholders();

	@Internal
	public default void setPlaceholders(Placeholders holders) {
		if (getPlaceholderPrefix().isPresent()) {
			holders.setPlaceholderPrefix(getPlaceholderPrefix().get());
		}
		if (getPlaceholderSuffix().isPresent()) {
			holders.setPlaceholderSuffix(getPlaceholderSuffix().get());
		}
		if (getPlaceholders().isPresent()) {
			holders.setPlaceholders(getPlaceholders().get());
		}
	}
}
