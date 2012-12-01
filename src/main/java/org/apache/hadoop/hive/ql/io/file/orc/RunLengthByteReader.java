/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hive.ql.io.file.orc;

import java.io.EOFException;
import java.io.IOException;

/**
 * A reader that reads a sequence of bytes. A control byte is read before
 * each run with positive values 0 to 127 meaning 2 to 129 repetitions. If the
 * byte is -1 to -128, 1 to 128 literal byte values follow.
 */
class RunLengthByteReader {
  private final InStream input;
  private final byte[] literals =
    new byte[RunLengthByteWriter.MAX_LITERAL_SIZE];
  private int numLiterals = 0;
  private int used = 0;
  private boolean repeat = false;
  private boolean done = false;

  RunLengthByteReader(InStream input) throws IOException {
    this.input = input;
  }

  private void readValues() throws IOException {
    int control = input.read();
    used = 0;
    if (control == -1) {
      done = true;
    } else if (control < 0x80) {
      numLiterals = control + 2;
      int val = input.read();
      if (val == -1) {
        throw new EOFException("Reading RLE byte got EOF");
      }
      literals[0] = (byte) val;
    } else {
      numLiterals = 0x100 - control;
      int bytes = 0;
      while (bytes < numLiterals) {
        int result = input.read(literals, bytes, numLiterals - bytes);
        if (result == -1) {
          throw new EOFException("Reading RLE byte literal got EOF");
        }
        bytes += result;
      }
    }
  }

  boolean hasNext() throws IOException {
    if (used == numLiterals) {
      if (!done) {
        readValues();
      }
    }
    return !done && used != numLiterals;
  }

  byte next() throws IOException {
    if (repeat) {
      used += 1;
      return literals[0];
    } else {
      return literals[used++];
    }
  }

}