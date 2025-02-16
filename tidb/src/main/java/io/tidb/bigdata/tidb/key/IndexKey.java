/*
 * Copyright 2017 TiDB Project Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.tidb.bigdata.tidb.key;

import com.google.common.base.Joiner;
import io.tidb.bigdata.tidb.codec.Codec.IntegerCodec;
import io.tidb.bigdata.tidb.codec.CodecDataOutput;
import io.tidb.bigdata.tidb.handle.Handle;
import io.tidb.bigdata.tidb.meta.TiIndexColumn;
import io.tidb.bigdata.tidb.meta.TiTableInfo;
import io.tidb.bigdata.tidb.row.Row;
import io.tidb.bigdata.tidb.types.DataType;
import io.tidb.bigdata.tidb.types.IntegerType;
import java.util.List;
import org.tikv.common.exception.TypeException;

public class IndexKey extends Key {
  private static final byte[] IDX_PREFIX_SEP = new byte[] {'_', 'i'};

  private final long tableId;
  private final long indexId;
  private final Key[] dataKeys;

  private IndexKey(long tableId, long indexId, Key[] dataKeys) {
    super(encode(tableId, indexId, dataKeys));
    this.tableId = tableId;
    this.indexId = indexId;
    this.dataKeys = dataKeys;
  }

  public static class EncodeIndexDataResult {
    public EncodeIndexDataResult(Key[] keys, boolean appendHandle) {
      this.keys = keys;
      this.appendHandle = appendHandle;
    }

    public Key[] keys;
    public boolean appendHandle;
  }

  public static IndexKey toIndexKey(long tableId, long indexId, Key... dataKeys) {
    return new IndexKey(tableId, indexId, dataKeys);
  }

  public static EncodeIndexDataResult encodeIndexDataValues(
      Row row,
      List<TiIndexColumn> indexColumns,
      Handle handle,
      boolean appendHandleIfContainsNull,
      TiTableInfo tableInfo) {
    // when appendHandleIfContainsNull is true, append handle column if any of the index column is
    // NULL
    boolean appendHandle = false;
    if (handle.isInt()) {
      if (appendHandleIfContainsNull) {
        for (TiIndexColumn col : indexColumns) {
          DataType colTp = tableInfo.getColumn(col.getOffset()).getType();
          if (row.get(col.getOffset(), colTp) == null) {
            appendHandle = true;
            break;
          }
        }
      }
    }

    Key[] keys = new Key[indexColumns.size() + (appendHandle ? 1 : 0)];
    for (int i = 0; i < indexColumns.size(); i++) {
      TiIndexColumn col = indexColumns.get(i);
      DataType colTp = tableInfo.getColumn(col.getOffset()).getType();
      // truncate index's if necessary
      Key key = TypedKey.toTypedKey(row.get(col.getOffset(), colTp), colTp, (int) col.getLength());
      keys[i] = key;
    }
    if (appendHandle) {
      Key key = TypedKey.toTypedKey(handle, IntegerType.BIGINT);
      keys[keys.length - 1] = key;
    }

    return new EncodeIndexDataResult(keys, appendHandle);
  }

  private static byte[] encode(long tableId, long indexId, Key[] dataKeys) {
    CodecDataOutput cdo = new CodecDataOutput();
    cdo.write(TBL_PREFIX);
    IntegerCodec.writeLong(cdo, tableId);
    cdo.write(IDX_PREFIX_SEP);
    IntegerCodec.writeLong(cdo, indexId);
    for (Key key : dataKeys) {
      if (key == null) {
        throw new TypeException("key cannot be null");
      }
      cdo.write(key.getBytes());
    }
    return cdo.toBytes();
  }

  public long getTableId() {
    return tableId;
  }

  public long getIndexId() {
    return indexId;
  }

  public Key[] getDataKeys() {
    return dataKeys;
  }

  @Override
  public String toString() {
    return String.format("[%s]", Joiner.on(",").useForNull("null").join(dataKeys));
  }
}
