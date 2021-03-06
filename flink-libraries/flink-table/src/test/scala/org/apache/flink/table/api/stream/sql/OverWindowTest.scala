/*
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

package org.apache.flink.table.api.stream.sql

import org.apache.flink.api.scala._
import org.apache.flink.table.api.scala._
import org.apache.flink.table.utils.TableTestUtil._
import org.apache.flink.table.utils.{StreamTableTestUtil, TableTestBase}
import org.junit.Test

class OverWindowTest extends TableTestBase {
  private val streamUtil: StreamTableTestUtil = streamTestUtil()
  streamUtil.addTable[(Int, String, Long)](
    "MyTable",
    'a, 'b, 'c,
    'proctime.proctime, 'rowtime.rowtime)

  @Test
  def testProcTimeBoundedPartitionedRowsOver() = {
    val sql = "SELECT " +
      "c, " +
      "count(a) OVER (PARTITION BY c ORDER BY proctime ROWS BETWEEN 2 preceding AND " +
      "CURRENT ROW) as cnt1, " +
      "sum(a) OVER (PARTITION BY c ORDER BY proctime ROWS BETWEEN 2 preceding AND " +
      "CURRENT ROW) as sum1 " +
      "from MyTable"

    val expected =
      unaryNode(
        "DataStreamCalc",
        unaryNode(
          "DataStreamOverAggregate",
          unaryNode(
            "DataStreamCalc",
            streamTableNode(0),
            term("select", "a", "c", "proctime")
          ),
          term("partitionBy", "c"),
          term("orderBy", "proctime"),
          term("rows", "BETWEEN 2 PRECEDING AND CURRENT ROW"),
          term("select", "a", "c", "proctime", "COUNT(a) AS w0$o0, $SUM0(a) AS w0$o1")
        ),
        term("select", "c", "w0$o0 AS cnt1, CASE(>(w0$o0, 0), CAST(w0$o1), null) AS sum1")
      )
    streamUtil.verifySql(sql, expected)
  }

  @Test
  def testProcTimeBoundedPartitionedRangeOver() = {

    val sqlQuery =
      "SELECT a, " +
        "  AVG(c) OVER (PARTITION BY a ORDER BY proctime " +
        "    RANGE BETWEEN INTERVAL '2' HOUR PRECEDING AND CURRENT ROW) AS avgA " +
        "FROM MyTable"

    val expected =
      unaryNode(
        "DataStreamCalc",
        unaryNode(
          "DataStreamOverAggregate",
          unaryNode(
            "DataStreamCalc",
            streamTableNode(0),
            term("select", "a", "c", "proctime")
          ),
          term("partitionBy", "a"),
          term("orderBy", "proctime"),
          term("range", "BETWEEN 7200000 PRECEDING AND CURRENT ROW"),
          term(
            "select",
            "a",
            "c",
            "proctime",
            "COUNT(c) AS w0$o0",
            "$SUM0(c) AS w0$o1"
          )
        ),
        term("select", "a", "/(CASE(>(w0$o0, 0)", "CAST(w0$o1), null), w0$o0) AS avgA")
      )

    streamUtil.verifySql(sqlQuery, expected)
  }

  @Test
  def testProcTimeBoundedNonPartitionedRangeOver() = {

    val sqlQuery =
      "SELECT a, " +
        "  COUNT(c) OVER (ORDER BY proctime " +
        "    RANGE BETWEEN INTERVAL '10' SECOND PRECEDING AND CURRENT ROW) " +
        "FROM MyTable"

    val expected =
      unaryNode(
        "DataStreamCalc",
        unaryNode(
          "DataStreamOverAggregate",
          unaryNode(
            "DataStreamCalc",
            streamTableNode(0),
            term("select", "a", "c", "proctime")
          ),
          term("orderBy", "proctime"),
          term("range", "BETWEEN 10000 PRECEDING AND CURRENT ROW"),
          term("select", "a", "c", "proctime", "COUNT(c) AS w0$o0")
        ),
        term("select", "a", "w0$o0 AS $1")
      )

    streamUtil.verifySql(sqlQuery, expected)
  }

  @Test
  def testProcTimeBoundedNonPartitionedRowsOver() = {
    val sql = "SELECT " +
      "c, " +
      "count(a) OVER (ORDER BY proctime ROWS BETWEEN 2 preceding AND " +
      "CURRENT ROW)" +
      "from MyTable"

    val expected =
      unaryNode(
        "DataStreamCalc",
        unaryNode(
          "DataStreamOverAggregate",
          unaryNode(
            "DataStreamCalc",
            streamTableNode(0),
            term("select", "a", "c", "proctime")
          ),
          term("orderBy", "proctime"),
          term("rows", "BETWEEN 2 PRECEDING AND CURRENT ROW"),
          term("select", "a", "c", "proctime", "COUNT(a) AS w0$o0")
        ),
        term("select", "c", "w0$o0 AS $1")
      )
    streamUtil.verifySql(sql, expected)
  }


  @Test
  def testProcTimeUnboundedPartitionedRangeOver() = {
    val sql = "SELECT " +
      "c, " +
      "count(a) OVER (PARTITION BY c ORDER BY proctime RANGE UNBOUNDED preceding) as cnt1, " +
      "sum(a) OVER (PARTITION BY c ORDER BY proctime RANGE UNBOUNDED preceding) as cnt2 " +
      "from MyTable"

    val expected =
      unaryNode(
        "DataStreamCalc",
        unaryNode(
          "DataStreamOverAggregate",
          unaryNode(
            "DataStreamCalc",
            streamTableNode(0),
            term("select", "a", "c", "proctime")
          ),
          term("partitionBy", "c"),
          term("orderBy", "proctime"),
          term("range", "BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW"),
          term("select", "a", "c", "proctime", "COUNT(a) AS w0$o0", "$SUM0(a) AS w0$o1")
        ),
        term("select", "c", "w0$o0 AS cnt1", "CASE(>(w0$o0, 0)", "CAST(w0$o1), null) AS cnt2")
      )
    streamUtil.verifySql(sql, expected)
  }

  @Test
  def testProcTimeUnboundedPartitionedRowsOver() = {
    val sql = "SELECT " +
      "c, " +
      "count(a) OVER (PARTITION BY c ORDER BY proctime ROWS BETWEEN UNBOUNDED preceding AND " +
      "CURRENT ROW) " +
      "from MyTable"

    val expected =
      unaryNode(
        "DataStreamCalc",
        unaryNode(
          "DataStreamOverAggregate",
          streamTableNode(0),
          term("partitionBy", "c"),
          term("orderBy", "proctime"),
          term("rows", "BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW"),
          term("select", "a", "b", "c", "proctime", "rowtime", "COUNT(a) AS w0$o0")
        ),
        term("select", "c", "w0$o0 AS $1")
      )
    streamUtil.verifySql(sql, expected)
  }

  @Test
  def testProcTimeUnboundedNonPartitionedRangeOver() = {
    val sql = "SELECT " +
      "c, " +
      "count(a) OVER (ORDER BY proctime RANGE UNBOUNDED preceding) as cnt1, " +
      "sum(a) OVER (ORDER BY proctime RANGE UNBOUNDED preceding) as cnt2 " +
      "from MyTable"

    val expected =
      unaryNode(
        "DataStreamCalc",
        unaryNode(
          "DataStreamOverAggregate",
          unaryNode(
            "DataStreamCalc",
            streamTableNode(0),
            term("select", "a", "c", "proctime")
          ),
          term("orderBy", "proctime"),
          term("range", "BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW"),
          term("select", "a", "c", "proctime", "COUNT(a) AS w0$o0", "$SUM0(a) AS w0$o1")
        ),
        term("select", "c", "w0$o0 AS cnt1", "CASE(>(w0$o0, 0)", "CAST(w0$o1), null) AS cnt2")
      )
    streamUtil.verifySql(sql, expected)
  }

  @Test
  def testProcTimeUnboundedNonPartitionedRowsOver() = {
    val sql = "SELECT " +
      "c, " +
      "count(a) OVER (ORDER BY proctime ROWS BETWEEN UNBOUNDED preceding AND " +
      "CURRENT ROW) " +
      "from MyTable"

    val expected =
      unaryNode(
        "DataStreamCalc",
        unaryNode(
          "DataStreamOverAggregate",
          streamTableNode(0),
          term("orderBy", "proctime"),
          term("rows", "BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW"),
          term("select", "a", "b", "c", "proctime", "rowtime", "COUNT(a) AS w0$o0")
        ),
        term("select", "c", "w0$o0 AS $1")
      )
    streamUtil.verifySql(sql, expected)
  }

  @Test
  def testRowTimeBoundedPartitionedRowsOver() = {
    val sql = "SELECT " +
      "c, " +
      "count(a) OVER (PARTITION BY c ORDER BY rowtime ROWS BETWEEN 5 preceding AND " +
      "CURRENT ROW) " +
      "from MyTable"

    val expected =
      unaryNode(
        "DataStreamCalc",
        unaryNode(
          "DataStreamOverAggregate",
          unaryNode(
            "DataStreamCalc",
            streamTableNode(0),
            term("select", "a", "c", "rowtime")
          ),
          term("partitionBy", "c"),
          term("orderBy", "rowtime"),
          term("rows", "BETWEEN 5 PRECEDING AND CURRENT ROW"),
          term("select", "a", "c", "rowtime", "COUNT(a) AS w0$o0")
        ),
        term("select", "c", "w0$o0 AS $1")
      )
    streamUtil.verifySql(sql, expected)
  }

  @Test
  def testRowTimeBoundedPartitionedRangeOver() = {
    val sql = "SELECT " +
      "c, " +
      "count(a) OVER (PARTITION BY c ORDER BY rowtime " +
      "RANGE BETWEEN INTERVAL '1' SECOND  preceding AND CURRENT ROW) " +
      "from MyTable"

    val expected =
      unaryNode(
        "DataStreamCalc",
        unaryNode(
          "DataStreamOverAggregate",
          unaryNode(
            "DataStreamCalc",
            streamTableNode(0),
            term("select", "a", "c", "rowtime")
          ),
          term("partitionBy", "c"),
          term("orderBy", "rowtime"),
          term("range", "BETWEEN 1000 PRECEDING AND CURRENT ROW"),
          term("select", "a", "c", "rowtime", "COUNT(a) AS w0$o0")
        ),
        term("select", "c", "w0$o0 AS $1")
      )
    streamUtil.verifySql(sql, expected)
  }

  @Test
  def testRowTimeBoundedNonPartitionedRowsOver() = {
    val sql = "SELECT " +
      "c, " +
      "count(a) OVER (ORDER BY rowtime ROWS BETWEEN 5 preceding AND " +
      "CURRENT ROW) " +
      "from MyTable"

    val expected =
      unaryNode(
        "DataStreamCalc",
        unaryNode(
          "DataStreamOverAggregate",
          unaryNode(
            "DataStreamCalc",
            streamTableNode(0),
            term("select", "a", "c", "rowtime")
          ),
          term("orderBy", "rowtime"),
          term("rows", "BETWEEN 5 PRECEDING AND CURRENT ROW"),
          term("select", "a", "c", "rowtime", "COUNT(a) AS w0$o0")
        ),
        term("select", "c", "w0$o0 AS $1")
      )
    streamUtil.verifySql(sql, expected)
  }

  @Test
  def testRowTimeBoundedNonPartitionedRangeOver() = {
    val sql = "SELECT " +
      "c, " +
      "count(a) OVER (ORDER BY rowtime " +
      "RANGE BETWEEN INTERVAL '1' SECOND  preceding AND CURRENT ROW) as cnt1 " +
      "from MyTable"

    val expected =
      unaryNode(
        "DataStreamCalc",
        unaryNode(
          "DataStreamOverAggregate",
          unaryNode(
            "DataStreamCalc",
            streamTableNode(0),
            term("select", "a", "c", "rowtime")
          ),
          term("orderBy", "rowtime"),
          term("range", "BETWEEN 1000 PRECEDING AND CURRENT ROW"),
          term("select", "a", "c", "rowtime", "COUNT(a) AS w0$o0")
        ),
        term("select", "c", "w0$o0 AS $1")
      )
    streamUtil.verifySql(sql, expected)
  }

  @Test
  def testRowTimeUnboundedPartitionedRangeOver() = {
    val sql = "SELECT " +
      "c, " +
      "count(a) OVER (PARTITION BY c ORDER BY rowtime RANGE UNBOUNDED preceding) as cnt1, " +
      "sum(a) OVER (PARTITION BY c ORDER BY rowtime RANGE UNBOUNDED preceding) as cnt2 " +
      "from MyTable"

    val expected =
      unaryNode(
        "DataStreamCalc",
        unaryNode(
          "DataStreamOverAggregate",
          unaryNode(
            "DataStreamCalc",
            streamTableNode(0),
            term("select", "a", "c", "rowtime")
          ),
          term("partitionBy", "c"),
          term("orderBy", "rowtime"),
          term("range", "BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW"),
          term("select", "a", "c", "rowtime", "COUNT(a) AS w0$o0", "$SUM0(a) AS w0$o1")
        ),
        term("select", "c", "w0$o0 AS cnt1", "CASE(>(w0$o0, 0)", "CAST(w0$o1), null) AS cnt2")
      )
    streamUtil.verifySql(sql, expected)
  }

  @Test
  def testRowTimeUnboundedPartitionedRowsOver() = {
    val sql = "SELECT " +
      "c, " +
      "count(a) OVER (PARTITION BY c ORDER BY rowtime ROWS UNBOUNDED preceding) as cnt1, " +
      "sum(a) OVER (PARTITION BY c ORDER BY rowtime ROWS UNBOUNDED preceding) as cnt2 " +
      "from MyTable"

    val expected =
      unaryNode(
        "DataStreamCalc",
        unaryNode(
          "DataStreamOverAggregate",
          unaryNode(
            "DataStreamCalc",
            streamTableNode(0),
            term("select", "a", "c", "rowtime")
          ),
          term("partitionBy", "c"),
          term("orderBy", "rowtime"),
          term("rows", "BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW"),
          term(
            "select",
            "a",
            "c",
            "rowtime",
            "COUNT(a) AS w0$o0",
            "$SUM0(a) AS w0$o1"
          )
        ),
        term(
          "select",
          "c",
          "w0$o0 AS cnt1",
          "CASE(>(w0$o0, 0)",
          "CAST(w0$o1), null) AS cnt2"
        )
      )
    streamUtil.verifySql(sql, expected)
  }

  @Test
  def testRowTimeUnboundedNonPartitionedRangeOver() = {
    val sql = "SELECT " +
      "c, " +
      "count(a) OVER (ORDER BY rowtime RANGE UNBOUNDED preceding) as cnt1, " +
      "sum(a) OVER (ORDER BY rowtime RANGE UNBOUNDED preceding) as cnt2 " +
      "from MyTable"

    val expected =
      unaryNode(
        "DataStreamCalc",
        unaryNode(
          "DataStreamOverAggregate",
          unaryNode(
            "DataStreamCalc",
            streamTableNode(0),
            term("select", "a", "c", "rowtime")
          ),
          term("orderBy", "rowtime"),
          term("range", "BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW"),
          term("select", "a", "c", "rowtime", "COUNT(a) AS w0$o0", "$SUM0(a) AS w0$o1")
        ),
        term("select", "c", "w0$o0 AS cnt1", "CASE(>(w0$o0, 0)", "CAST(w0$o1), null) AS cnt2")
      )
    streamUtil.verifySql(sql, expected)
  }

  @Test
  def testRowTimeUnboundedNonPartitionedRowsOver() = {
    val sql = "SELECT " +
      "c, " +
      "count(a) OVER (ORDER BY rowtime ROWS UNBOUNDED preceding) as cnt1, " +
      "sum(a) OVER (ORDER BY rowtime ROWS UNBOUNDED preceding) as cnt2 " +
      "from MyTable"

    val expected =
      unaryNode(
        "DataStreamCalc",
        unaryNode(
          "DataStreamOverAggregate",
          unaryNode(
            "DataStreamCalc",
            streamTableNode(0),
            term("select", "a", "c", "rowtime")
          ),
          term("orderBy", "rowtime"),
          term("rows", "BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW"),
          term(
            "select",
            "a",
            "c",
            "rowtime",
            "COUNT(a) AS w0$o0",
            "$SUM0(a) AS w0$o1"
          )
        ),
        term(
          "select",
          "c",
          "w0$o0 AS cnt1",
          "CASE(>(w0$o0, 0)",
          "CAST(w0$o1), null) AS cnt2"
        )
      )
    streamUtil.verifySql(sql, expected)
  }
}
