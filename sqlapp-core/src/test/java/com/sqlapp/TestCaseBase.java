/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp;


import java.util.Locale;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * 初期値を設定するテスト基底クラス。
 * 
 */
public abstract class TestCaseBase {
	static {
		setTimeZone();
	}

	protected static void setTimeZone() {
		Locale.setDefault(Locale.UK);
		TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
	}

	protected static void setTimeZoneJST() {
		Locale.setDefault(Locale.JAPANESE);
		TimeZone.setDefault(TimeZone.getTimeZone("JST"));
	}

	/**
	 * テスト開始時に状態を初期化します。
	 */
	@BeforeEach
	public void setUpTestCaseBase() {
	}

	/**
	 * テスト終了時に状態をクリーンアップします。
	 */
	@AfterEach
	public void tearDownTestCaseBase() {
	}
}
