package com.sqlapp.jdbc.sql;

import java.util.function.Consumer;

import com.sqlapp.jdbc.ExResultSet;

/**
 * ExResultSet用のハンドラー
 */
@FunctionalInterface
public interface ResultSetHandler extends Consumer<ExResultSet> {

}
