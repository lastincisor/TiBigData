/*
 * Copyright 2020 TiDB Project Authors.
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

package io.tidb.bigdata.tidb.codec;

import io.tidb.bigdata.tidb.handle.CommonHandle;
import io.tidb.bigdata.tidb.handle.Handle;
import io.tidb.bigdata.tidb.handle.IntHandle;
import io.tidb.bigdata.tidb.meta.TiColumnInfo;
import io.tidb.bigdata.tidb.meta.TiIndexInfo;
import io.tidb.bigdata.tidb.meta.TiTableInfo;
import io.tidb.bigdata.tidb.row.Row;
import java.util.List;
import org.tikv.common.exception.CodecException;

public class TableCodec {
  public static byte IndexVersionFlag = 125;
  public static byte CommonHandleFlag = 127;

  public static byte[] encodeRow(
      List<TiColumnInfo> columnInfos,
      Object[] values,
      boolean isPkHandle,
      boolean encodeWithNewRowFormat)
      throws IllegalAccessException {
    if (columnInfos.size() != values.length) {
      throw new IllegalAccessException(
          String.format(
              "encodeRow error: data and columnID count not " + "match %d vs %d",
              columnInfos.size(), values.length));
    }
    if (encodeWithNewRowFormat) {
      return TableCodecV2.encodeRow(columnInfos, values, isPkHandle);
    }
    return TableCodecV1.encodeRow(columnInfos, values, isPkHandle);
  }

  public static Row decodeRow(byte[] value, Handle handle, TiTableInfo tableInfo) {
    if (value.length == 0) {
      throw new CodecException("Decode fails: value length is zero");
    }
    if ((value[0] & 0xff) == RowV2.CODEC_VER) {
      return TableCodecV2.decodeRow(value, handle, tableInfo);
    }
    return TableCodecV1.decodeRow(value, handle, tableInfo);
  }

  public static Handle decodeHandle(byte[] value, boolean isCommonHandle) {
    if (isCommonHandle) {
      return new CommonHandle(value);
    }
    return new IntHandle(new CodecDataInput(value).readLong());
  }

  /* only for unique index */
  public static byte[] genIndexValueForClusteredIndexVersion1(TiIndexInfo index, Handle handle) {
    CodecDataOutput cdo = new CodecDataOutput();
    cdo.writeByte(0);
    cdo.writeByte(IndexVersionFlag);
    cdo.writeByte(1);

    assert (index.isUnique());
    encodeCommonHandle(cdo, handle);

    return cdo.toBytes();
  }

  private static void encodeCommonHandle(CodecDataOutput cdo, Handle handle) {
    cdo.write(CommonHandleFlag);
    byte[] encoded = handle.encoded();
    int hLen = encoded.length;
    cdo.writeShort(hLen);
    cdo.write(encoded);
  }
}
