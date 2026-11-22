/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mdb.
 *
 * sqlapp-core-mdb is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mdb is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mdb.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

/**
 * Microsoft Access(MDB/ACCDB)の方言のサポート。
 * <p>
 * Access 97(Jet 3)のファイルはテキストをコードページで保存しているため、日本語を含む場合は ファイルを開く前にシステムプロパティ
 * {@code jackcess.charset.VERSION_3} (例:
 * {@code -Djackcess.charset.VERSION_3=MS932})を設定してください。 Jackcess と UCanAccess
 * の両方に適用されます。Jet 4 以降は Unicode のため不要です。
 * </p>
 */
package com.sqlapp.data.db.dialect.mdb;
