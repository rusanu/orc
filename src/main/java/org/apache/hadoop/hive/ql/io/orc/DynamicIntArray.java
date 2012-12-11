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
package org.apache.hadoop.hive.ql.io.orc;

/**
 * simplistic dynamic array that differentiates from ArrayList by
 *  - copying pointers to chunks of ints instead of copying the ints
 *  - managing primitive 'int' types, i.e. not requiring box objects
 *
 * the motivation for this class is memory optimization, i.e. space efficient
 * storage of potentially huge arrays without good a-priori size guesses
 *
 * the API of this class is between a primitive array and a AbstractList. It's
 * not a Collection implementation because it handles primitive types, but the
 * API could be extended to support iterators and the like.
 *
 * NOTE: like standard Collection implementations/arrays, this class is not
 * synchronized
 */
class DynamicIntArray {
  final static int DEFAULT_CHUNKSIZE = 8 * 1024;
  final static int INIT_CHUNKS = 128;

  final int chunkSize;  /** our allocation sizes */
  int[][] data;   /** the real data */
  int length;     /** max set element index +1 */
  int initializedChunks = 0;

  public DynamicIntArray () {
    this( DEFAULT_CHUNKSIZE);
  }

  public DynamicIntArray (int chunkSize) {
    this.chunkSize = chunkSize;

    data = new int[INIT_CHUNKS][];
  }

  public boolean isEmpty () {
    return (length == 0);
  }

  /**
   * Ensure that the given index is valid.
   */
  private void grow(int chunkIndex) {
    if (chunkIndex >= initializedChunks) {
      if (chunkIndex >= data.length) {
        int new_size = Math.max(chunkIndex, 2 * data.length);
        int[][] newChunk = new int[new_size][];
        System.arraycopy(data, 0, newChunk, 0, data.length);
        data = newChunk;
      }
      for(int i=initializedChunks; i <= chunkIndex; ++i) {
        data[i] = new int[chunkSize];
      }
      initializedChunks = chunkIndex + 1;
    }
  }

  public int get (int index) {
    if (index >= length) {
      throw new IndexOutOfBoundsException("Index " + index +
                                            " is outside of 0.." +
                                            (length - 1));
    }
    int i = index / chunkSize;
    int j = index % chunkSize;
    return data[i][j];
  }

  public void set (int index, int value) {
    int i = index / chunkSize;
    int j = index % chunkSize;
    grow(i);
    if (index > length) {
      length = index;
    }
    data[i][j] = value;
  }

  public void add(int value) {
    int i = length / chunkSize;
    int j = length % chunkSize;
    grow(i);
    data[i][j] = value;
    length += 1;
  }

  public int size() {
    return length;
  }

  public String toString() {
    int i;
    StringBuffer sb = new StringBuffer(length*4);

    sb.append('{');
    int l = length-1;
    for (i=0; i<l; i++) {
      sb.append(get(i));
      sb.append(',');
    }
    sb.append(get(i));
    sb.append('}');

    return sb.toString();
  }

  public int[] toArray (int[] buf) {
    if (buf.length < length) {
      buf = new int[length];
    }
    for (int i=0; i<length; i++) {
      buf[i] = get(i);
    }

    return buf;
  }

}
