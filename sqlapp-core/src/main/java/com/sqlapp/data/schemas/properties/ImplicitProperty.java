package com.sqlapp.data.schemas.properties;

public interface ImplicitProperty<T> {
	boolean isImplicit();
	T setImplicit(boolean value);
}
