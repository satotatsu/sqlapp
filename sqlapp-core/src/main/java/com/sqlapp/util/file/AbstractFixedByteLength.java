/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.util.file;

import java.nio.charset.Charset;
import java.util.function.Consumer;

/**
 * バイト長固定ファイルの抽象クラス
 * @author satot
 *
 */
public class AbstractFixedByteLength {
	private final Charset charset;
	private final FixedByteLengthFileSetting setting;
	
	AbstractFixedByteLength(final FixedByteLengthFileSetting setting , final Charset charset, final Consumer<FixedByteLengthFileSetting> cons){
		this.setting=setting;
		this.charset=charset;
		cons.accept(setting);
	}
	
	protected FixedByteLengthFileSetting getSetting() {
		return setting;
	}
	
	protected FixedByteLengthFileSetting getCharsetSetting() {
		final FixedByteLengthFileSetting setting=getSetting();
		setting.initialize(charset);
		return setting;
	}
}
