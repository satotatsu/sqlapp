/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test;

import java.lang.reflect.*;
import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/** Counts executed SQL, including ordinary and prepared statements. */
public final class CountingJdbc {
	private CountingJdbc() { }
	public static Connection wrap(Connection connection, Predicate<String> selected, AtomicInteger count) {
		return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] { Connection.class },
				(proxy, method, args) -> {
					Object result = invoke(method, connection, args);
					if (result instanceof Statement statement && (method.getName().equals("prepareStatement") || method.getName().equals("createStatement"))) {
						String prepared = method.getName().equals("prepareStatement") ? (String) args[0] : null;
						Class<?> api = prepared == null ? Statement.class : PreparedStatement.class;
						return Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] { api }, (p, m, a) -> {
							if (m.getName().startsWith("execute")) {
								String sql = prepared != null ? prepared : a != null && a.length > 0 && a[0] instanceof String ? (String) a[0] : "";
								if (selected.test(sql)) count.incrementAndGet();
							}
							return invoke(m, statement, a);
						});
					}
					return result;
				});
	}
	private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
		try { return method.invoke(target, args); }
		catch (InvocationTargetException e) { throw e.getCause(); }
	}
}
