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

import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;

class ColumnStatisticsImpl implements ColumnStatistics {

  private final static class BooleanStatisticsImpl extends ColumnStatisticsImpl
      implements BooleanColumnStatistics {
    private long trueCount = 0;

    BooleanStatisticsImpl(OrcProto.ColumnStatistics stats) {
      super(stats);
      OrcProto.BucketStatistics bkt = stats.getBucketStatistics();
      trueCount = bkt.getCount(1);
    }

    BooleanStatisticsImpl(int columnId) {
      super(columnId);
    }

    @Override
    void reset() {
      super.reset();
      trueCount = 0;
    }

    @Override
    void updateBoolean(boolean value) {
      if (value) {
        trueCount += 1;
      }
    }

    @Override
    void merge(ColumnStatisticsImpl other) {
      super.merge(other);
      BooleanStatisticsImpl bkt = (BooleanStatisticsImpl) other;
      trueCount += bkt.trueCount;
    }

    @Override
    OrcProto.ColumnStatistics.Builder serialize() {
      OrcProto.ColumnStatistics.Builder builder = super.serialize();
      OrcProto.BucketStatistics.Builder bucket =
        OrcProto.BucketStatistics.newBuilder();
      bucket.addCount(count - trueCount);
      bucket.addCount(trueCount);
      builder.setBucketStatistics(bucket);
      return builder;
    }

    @Override
    public long getFalseCount() {
      return count - trueCount;
    }

    @Override
    public long getTrueCount() {
      return trueCount;
    }

    @Override
    public String toString() {
      return super.toString() + " true: " + trueCount;
    }
  }

  private final static class IntegerStatisticsImpl extends ColumnStatisticsImpl
      implements IntegerColumnStatistics {

    long minimum = Long.MAX_VALUE;
    long maximum = Long.MIN_VALUE;
    long sum = 0;
    boolean overflow = false;

    IntegerStatisticsImpl(int columnId) {
      super(columnId);
    }

    IntegerStatisticsImpl(OrcProto.ColumnStatistics stats) {
      super(stats);
      OrcProto.IntegerStatistics intStat = stats.getIntStatistics();
      if (intStat.hasMinimum()) {
        minimum = intStat.getMinimum();
      }
      if (intStat.hasMaximum()) {
        maximum = intStat.getMaximum();
      }
      if (intStat.hasSum()) {
        sum = intStat.getSum();
      } else {
        overflow = true;
      }
    }

    @Override
    void reset() {
      super.reset();
      minimum = Long.MAX_VALUE;
      maximum = Long.MIN_VALUE;
      sum = 0;
      overflow = false;
    }

    @Override
    void updateInteger(long value) {
      if (count == 0) {
        minimum = value;
        maximum = value;
      } else if (value < minimum) {
        minimum = value;
      } else if (value > maximum) {
        maximum = value;
      }
      if (!overflow) {
        boolean wasPositive = sum >= 0;
        sum += value;
        if ((value >= 0) == wasPositive) {
          overflow = (sum >= 0) != wasPositive;
        }
      }
    }

    @Override
    void merge(ColumnStatisticsImpl other) {
      IntegerStatisticsImpl otherInt = (IntegerStatisticsImpl) other;
      if (count == 0) {
        minimum = otherInt.minimum;
        maximum = otherInt.maximum;
      } else if (otherInt.minimum < minimum) {
        minimum = otherInt.minimum;
      } else if (otherInt.maximum > maximum) {
        maximum = otherInt.maximum;
      }
      super.merge(other);
      overflow |= otherInt.overflow;
      if (!overflow) {
        boolean wasPositive = sum >= 0;
        sum += otherInt.sum;
        if ((otherInt.sum >= 0) == wasPositive) {
          overflow = (sum >= 0) != wasPositive;
        }
      }
    }

    @Override
    OrcProto.ColumnStatistics.Builder serialize() {
      OrcProto.ColumnStatistics.Builder builder = super.serialize();
      OrcProto.IntegerStatistics.Builder intb =
        OrcProto.IntegerStatistics.newBuilder();
      if (count != 0) {
        intb.setMinimum(minimum);
        intb.setMaximum(maximum);
      }
      if (!overflow) {
        intb.setSum(sum);
      }
      builder.setIntStatistics(intb);
      return builder;
    }

    @Override
    public long getMinimum() {
      return minimum;
    }

    @Override
    public long getMaximum() {
      return maximum;
    }

    @Override
    public boolean isSumDefined() {
      return !overflow;
    }

    @Override
    public long getSum() {
      return sum;
    }

    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(super.toString());
      if (count != 0) {
        buf.append(" min: ");
        buf.append(minimum);
        buf.append(" max: ");
        buf.append(maximum);
      }
      if (!overflow) {
        buf.append(" sum: ");
        buf.append(sum);
      }
      return buf.toString();
    }
  }

  private final static class DoubleStatisticsImpl extends ColumnStatisticsImpl
       implements DoubleColumnStatistics {
    double minimum = Double.MAX_VALUE;
    double maximum = Double.MIN_VALUE;
    double sum = 0;

    DoubleStatisticsImpl(int columnId) {
      super(columnId);
    }

    DoubleStatisticsImpl(OrcProto.ColumnStatistics stats) {
      super(stats);
      OrcProto.DoubleStatistics dbl = stats.getDoubleStatistics();
      if (dbl.hasMinimum()) {
        minimum = dbl.getMinimum();
      }
      if (dbl.hasMaximum()) {
        maximum = dbl.getMaximum();
      }
      if (dbl.hasSum()) {
        sum = dbl.getSum();
      }
    }

    @Override
    void reset() {
      super.reset();
      minimum = Double.MAX_VALUE;
      maximum = Double.MIN_VALUE;
      sum = 0;
    }

    @Override
    void updateDouble(double value) {
      if (count == 0) {
        minimum = value;
        maximum = value;
      } else if (value < minimum) {
        minimum = value;
      } else if (value > maximum) {
        maximum = value;
      }
      sum += value;
    }

    @Override
    void merge(ColumnStatisticsImpl other) {
      DoubleStatisticsImpl dbl = (DoubleStatisticsImpl) other;
      if (count == 0) {
        minimum = dbl.minimum;
        maximum = dbl.maximum;
      } else if (dbl.minimum < minimum) {
        minimum = dbl.minimum;
      } else if (dbl.maximum > maximum) {
        maximum = dbl.maximum;
      }
      super.merge(other);
      sum += dbl.sum;
    }

    @Override
    OrcProto.ColumnStatistics.Builder serialize() {
      OrcProto.ColumnStatistics.Builder builder = super.serialize();
      OrcProto.DoubleStatistics.Builder dbl =
        OrcProto.DoubleStatistics.newBuilder();
      if (count != 0) {
        dbl.setMinimum(minimum);
        dbl.setMaximum(maximum);
      }
      dbl.setSum(sum);
      builder.setDoubleStatistics(dbl);
      return builder;
    }

    @Override
    public double getMinimum() {
      return minimum;
    }

    @Override
    public double getMaximum() {
      return maximum;
    }

    @Override
    public double getSum() {
      return sum;
    }
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(super.toString());
      if (count != 0) {
        buf.append(" min: ");
        buf.append(minimum);
        buf.append(" max: ");
        buf.append(maximum);
      }
      buf.append(" sum: ");
      buf.append(sum);
      return buf.toString();
    }
  }

  private final static class StringStatisticsImpl extends ColumnStatisticsImpl
      implements StringColumnStatistics {
    String minimum = null;
    String maximum = null;

    StringStatisticsImpl(int columnId) {
      super(columnId);
    }

    StringStatisticsImpl(OrcProto.ColumnStatistics stats) {
      super(stats);
      OrcProto.StringStatistics str = stats.getStringStatistics();
      if (str.hasMaximum()) {
        maximum = str.getMaximum();
      }
      if (str.hasMinimum()) {
        minimum = str.getMinimum();
      }
    }

    @Override
    void reset() {
      super.reset();
      minimum = null;
      maximum = null;
    }

    @Override
    void updateString(String value) {
      if (count == 0) {
        minimum = value;
        maximum = value;
      } else if (minimum.compareTo(value) > 0) {
        minimum = value;
      } else if (maximum.compareTo(value) < 0) {
        maximum = value;
      }
    }

    @Override
    void merge(ColumnStatisticsImpl other) {
      StringStatisticsImpl str = (StringStatisticsImpl) other;
      if (count == 0) {
        minimum = str.minimum;
        maximum = str.maximum;
      } else if (minimum.compareTo(str.minimum) > 0) {
        minimum = str.minimum;
      } else if (maximum.compareTo(str.maximum) < 0) {
        maximum = str.maximum;
      }
      super.merge(other);
    }

    @Override
    OrcProto.ColumnStatistics.Builder serialize() {
      OrcProto.ColumnStatistics.Builder result = super.serialize();
      OrcProto.StringStatistics.Builder str =
        OrcProto.StringStatistics.newBuilder();
      if (count != 0) {
        str.setMinimum(minimum);
        str.setMaximum(maximum);
      }
      result.setStringStatistics(str);
      return result;
    }

    @Override
    public String getMinimum() {
      return minimum;
    }

    @Override
    public String getMaximum() {
      return maximum;
    }
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(super.toString());
      if (count != 0) {
        buf.append(" min: ");
        buf.append(minimum);
        buf.append(" max: ");
        buf.append(maximum);
      }
      return buf.toString();
    }
  }

  private final int columnId;
  protected long count = 0;

  ColumnStatisticsImpl(OrcProto.ColumnStatistics stats) {
    if (stats.hasColumn()) {
      columnId = stats.getColumn();
    } else {
      columnId = 0;
    }
    if (stats.hasNumberOfValues()) {
      count = stats.getNumberOfValues();
    }
  }

  ColumnStatisticsImpl(int columnId) {
    this.columnId = columnId;
  }

  void increment() {
    count += 1;
  }

  void updateBoolean(boolean value) {
    throw new UnsupportedOperationException("Can't update boolean");
  }

  void updateInteger(long value) {
    throw new UnsupportedOperationException("Can't update integer");
  }

  void updateDouble(double value) {
    throw new UnsupportedOperationException("Can't update double");
  }

  void updateString(String value) {
    throw new UnsupportedOperationException("Can't update string");
  }

  void merge(ColumnStatisticsImpl stats) {
    if (columnId != stats.columnId) {
      throw new IllegalArgumentException("Unmergeable column statistics");
    }
    count += stats.count;
  }

  void reset() {
    count = 0;
  }

  @Override
  public long getNumberOfValues() {
    return count;
  }

  @Override
  public String toString() {
    return "count: " + count;
  }

  OrcProto.ColumnStatistics.Builder serialize() {
    OrcProto.ColumnStatistics.Builder builder =
      OrcProto.ColumnStatistics.newBuilder();
    builder.setColumn(columnId);
    builder.setNumberOfValues(count);
    return builder;
  }

  static ColumnStatisticsImpl create(int columnId,
                                     ObjectInspector inspector) {
    switch (inspector.getCategory()) {
      case PRIMITIVE:
        switch (((PrimitiveObjectInspector) inspector).getPrimitiveCategory()) {
          case BOOLEAN:
            return new BooleanStatisticsImpl(columnId);
          case BYTE:
          case SHORT:
          case INT:
          case LONG:
            return new IntegerStatisticsImpl(columnId);
          case FLOAT:
          case DOUBLE:
            return new DoubleStatisticsImpl(columnId);
          case STRING:
            return new StringStatisticsImpl(columnId);
          default:
            return new ColumnStatisticsImpl(columnId);
        }
      default:
        return new ColumnStatisticsImpl(columnId);
    }
  }

  static ColumnStatisticsImpl deserialize(OrcProto.ColumnStatistics stats) {
    if (stats.hasBucketStatistics()) {
      return new BooleanStatisticsImpl(stats);
    } else if (stats.hasIntStatistics()) {
      return new IntegerStatisticsImpl(stats);
    } else if (stats.hasDoubleStatistics()) {
      return new DoubleStatisticsImpl(stats);
    } else if (stats.hasStringStatistics()) {
      return new StringStatisticsImpl(stats);
    } else {
      return new ColumnStatisticsImpl(stats);
    }
  }
}