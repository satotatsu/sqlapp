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

package com.sqlapp.data.db.metadata;

import java.io.Serializable;

import com.sqlapp.data.db.sql.AbstractBean;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 読み込み時のオプション
 *
 */
@Data
@EqualsAndHashCode(callSuper=true)
@ToString(callSuper=true)
public class ReaderOptions extends AbstractBean implements Serializable{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -2915653831151929039L;
	/**
	 * definitionを読み込める場合は読み込みを行うフラグ
	 */
	private boolean readDefinition=false;
	/**
	 * statementを読み込める場合は読み込みを行うフラグ
	 */
	private boolean readStatement=true;
	/**
	 * HIDDEN columnを含めるかを決める
	 */
	private boolean containsHiddenColumns=false;
	/**
	 * Systemオブジェクトを除外するかを決める
	 */
	private boolean excludeSystemObjects=false;
}
