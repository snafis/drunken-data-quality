package de.frosner.ddq.reporters

import java.text.SimpleDateFormat
import java.util.Date

import de.frosner.ddq.constraints._
import de.frosner.ddq.core._
import de.frosner.ddq.reporters.Log4jReporter.JSONMaybe
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.types.{IntegerType, StringType}
import org.apache.spark.sql.{Column, DataFrame}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, FlatSpec}

import scala.util.parsing.json.{JSONArray, JSONObject}

class Log4jReporterTest extends FlatSpec with Matchers with MockitoSugar {

  private def checkJsonOf[T <: Constraint](constraintResult: ConstraintResult[T],
                                           additionalExpectedColumns: Map[String, Any]) = {
    val constraint = constraintResult.constraint
    val checkId = "check"
    val checkName = "df"
    val checkNumRows = 5L
    val check = Check(
      dataFrame = mock[DataFrame],
      displayName = Some(checkName),
      cacheMethod = None,
      constraints = Seq(constraint),
      id = checkId
    )
    val checkTime = new Date()
    val checkResult = CheckResult(Map(constraint -> constraintResult), check, checkNumRows)

    Log4jReporter.constraintResultToJson(checkResult, constraintResult, checkTime) shouldBe JSONObject(
      Map(
        Log4jReporter.checkKey -> JSONObject(Map(
          Log4jReporter.checkIdKey -> checkId,
          Log4jReporter.checkTimeKey -> checkTime.toString,
          Log4jReporter.checkNameKey -> checkName,
          Log4jReporter.checkNumRowsKey -> checkNumRows
        )),
        Log4jReporter.constraintTypeKey -> constraint.getClass.getSimpleName.replace("$", ""),
        Log4jReporter.constraintStatusKey -> constraintResult.status.stringValue,
        Log4jReporter.constraintMessageKey -> constraintResult.message
      ) ++ additionalExpectedColumns
    )
  }

  "JSON serialization" should "work for AlwaysNullConstraintResult" in {
    val columnName = "c"
    val nonNullRows = 5L
    val constraintResult = AlwaysNullConstraintResult(
      constraint = AlwaysNullConstraint(columnName),
      status = ConstraintFailure,
      nonNullRows = nonNullRows
    )

    checkJsonOf(constraintResult, Map(
      Log4jReporter.columnKey -> columnName,
      Log4jReporter.failedInstancesKey -> nonNullRows
    ))
  }

  it should "work for AnyOfConstraintResult" in {
    val columnName = "d"
    val allowedValues = Set("a", 5)
    val failedRows = 0L
    val constraintResult = AnyOfConstraintResult(
      constraint = AnyOfConstraint(columnName, allowedValues),
      status = ConstraintSuccess,
      failedRows = failedRows
    )

    checkJsonOf(constraintResult, Map(
      Log4jReporter.columnKey -> columnName,
      Log4jReporter.failedInstancesKey -> failedRows,
      "allowed" -> JSONArray(List("a", "5"))
    ))
  }

  it should "work for ColumnColumnConstraintResult" in {
    val constraint = new Column("c") > 5
    val failedRows = 0L
    val constraintResult = ColumnColumnConstraintResult(
      constraint = ColumnColumnConstraint(constraint),
      status = ConstraintSuccess,
      violatingRows = failedRows
    )

    checkJsonOf(constraintResult, Map(
      Log4jReporter.columnKey -> constraint.toString,
      Log4jReporter.failedInstancesKey -> failedRows
    ))
  }

  it should "work for ConditionalColumnConstraintResult" in {
    val statement = new Column("c") > 5
    val implication = new Column("d") > 6
    val failedRows = 0L
    val constraintResult = ConditionalColumnConstraintResult(
      constraint = ConditionalColumnConstraint(statement, implication),
      status = ConstraintSuccess,
      violatingRows = failedRows
    )

    checkJsonOf(constraintResult, Map(
      "statement" -> statement.toString,
      "implication" -> implication.toString,
      Log4jReporter.failedInstancesKey -> failedRows
    ))
  }

  it should "work for DateFormatConstraintResult" in {
    val column = "c"
    val formatString = "yyyy"
    val format = new SimpleDateFormat(formatString)
    val failedRows = 1L
    val constraintResult = DateFormatConstraintResult(
      constraint = DateFormatConstraint(column, format),
      status = ConstraintFailure,
      failedRows = failedRows
    )

    checkJsonOf(constraintResult, Map(
      Log4jReporter.columnKey -> column,
      Log4jReporter.failedInstancesKey -> failedRows,
      "dateFormat" -> formatString
    ))
  }

  it should "work for ForeignKeyConstraintResult" in {
    val columns = Seq("c" -> "d")
    val formatString = "yyyy"
    val format = new SimpleDateFormat(formatString)
    val failedRows = 1L
    val df = mock[DataFrame]
    val dfName = "df"
    when(df.toString).thenReturn(dfName)

    val constraintResult = ForeignKeyConstraintResult(
      constraint = ForeignKeyConstraint(columns, df),
      status = ConstraintFailure,
      numNonMatchingRefs = Some(failedRows)
    )

    checkJsonOf(constraintResult, Map(
      Log4jReporter.columnsKey -> JSONArray(List(
        JSONObject(Map("baseColumn" -> "c", "referenceColumn" -> "d"))
      )),
      "referenceTable" -> dfName,
      Log4jReporter.failedInstancesKey -> JSONMaybe(Some(failedRows))
    ))
  }

  it should "work for FunctionalDependencyConstraintResult" in {
    val determinantSet = Seq("a", "b")
    val dependentSet = Seq("c", "d")
    val failedRows = 1L
    val constraintResult = FunctionalDependencyConstraintResult(
      constraint = FunctionalDependencyConstraint(determinantSet, dependentSet),
      status = ConstraintFailure,
      failedRows = failedRows
    )

    checkJsonOf(constraintResult, Map(
      "determinantSet" -> JSONArray(List("a", "b")),
      "dependentSet" -> JSONArray(List("c", "d")),
      Log4jReporter.failedInstancesKey -> failedRows
    ))
  }

  it should "work for JoinableConstraintResult" in {
    val columns = Seq("c" -> "d")
    val formatString = "yyyy"
    val format = new SimpleDateFormat(formatString)
    val failedRows = 1L
    val df = mock[DataFrame]
    val dfName = "df"
    when(df.toString).thenReturn(dfName)
    val constraintResult = JoinableConstraintResult(
      constraint = JoinableConstraint(columns, df),
      distinctBefore = 5L,
      matchingKeys = 3L,
      status = ConstraintFailure
    )

    checkJsonOf(constraintResult, Map(
      Log4jReporter.columnsKey -> JSONArray(List(
        JSONObject(Map("baseColumn" -> "c", "referenceColumn" -> "d"))
      )),
      "referenceTable" -> dfName,
      "distinctBefore" -> 5L,
      "matchingKeys" -> 3L
    ))
  }

  it should "work for NeverNullConstraintResult" in {
    val column = "c"
    val failedRows = 1L
    val constraintResult = NeverNullConstraintResult(
      constraint = NeverNullConstraint(column),
      status = ConstraintFailure,
      nullRows = failedRows
    )

    checkJsonOf(constraintResult, Map(
      Log4jReporter.columnKey -> column,
      Log4jReporter.failedInstancesKey -> failedRows
    ))
  }

  it should "work for NumberOfRowsConstraintResult" in {
    val expected = new Column("count") === 1L
    val actual = 1L
    val constraintResult = NumberOfRowsConstraintResult(
      constraint = NumberOfRowsConstraint(expected),
      status = ConstraintSuccess,
      actual = actual
    )

    checkJsonOf(constraintResult, Map(
      "expected" -> expected.toString,
      "actual" -> actual
    ))
  }

  it should "work for RegexConstraintResult" in {
    val column = "c"
    val regex = "reg"
    val failedRows = 0L
    val constraintResult = RegexConstraintResult(
      constraint = RegexConstraint(column, regex),
      status = ConstraintSuccess,
      failedRows = failedRows
    )

    checkJsonOf(constraintResult, Map(
      Log4jReporter.columnKey -> column,
      Log4jReporter.failedInstancesKey -> failedRows,
      "regex" -> regex
    ))
  }

  it should "work for StringColumnConstraintResult" in {
    val column = "c"
    val failedRows = 0L
    val constraintResult = StringColumnConstraintResult(
      constraint = StringColumnConstraint(column),
      status = ConstraintSuccess,
      violatingRows = failedRows
    )

    checkJsonOf(constraintResult, Map(
      Log4jReporter.columnKey -> column,
      Log4jReporter.failedInstancesKey -> failedRows
    ))
  }

  it should "work for TypeConversionConstraintResult" in {
    val column = "c"
    val failedRows = 0L
    val constraintResult = TypeConversionConstraintResult(
      constraint = TypeConversionConstraint(column, StringType),
      status = ConstraintSuccess,
      failedRows = failedRows,
      originalType = IntegerType
    )

    checkJsonOf(constraintResult, Map(
      Log4jReporter.columnKey -> column,
      Log4jReporter.failedInstancesKey -> failedRows,
      "originalType" -> "IntegerType",
      "convertedType" -> "StringType"
    ))
  }

  it should "work for UniqueKeyConstraintResult" in {
    val columns = Seq("c", "d")
    val failedRows = 0L
    val constraintResult = UniqueKeyConstraintResult(
      constraint = UniqueKeyConstraint(columns),
      status = ConstraintSuccess,
      numNonUniqueTuples = failedRows
    )

    checkJsonOf(constraintResult, Map(
      Log4jReporter.columnsKey -> JSONArray(List("c", "d")),
      Log4jReporter.failedInstancesKey -> failedRows
    ))
  }

  "A log4j reporter" should "log correctly to the default logger with the default log level" in {
    val log4jReporter = Log4jReporter()

    val df = mock[DataFrame]
    val dfName = "myDf"
    val numRows = 10

    val result1 = ForeignKeyConstraintResult(ForeignKeyConstraint(Seq(("a", "b")), df), None, ConstraintFailure)
    val result2 = ForeignKeyConstraintResult(ForeignKeyConstraint(Seq(("a", "b"), ("c", "d")), df), Some(5), ConstraintFailure)

    val constraints = Map[Constraint, ConstraintResult[Constraint]](
      result1.constraint -> result1,
      result2.constraint -> result2
    )
    val check = Check(df, Some(dfName), Option.empty, constraints.keys.toSeq)

    log4jReporter.report(CheckResult(constraints, check, numRows))
  }

  it should "log the JSON format of a constraint result using the specified logger and log level" in {
    val logger = mock[Logger]
    val logLevel = Level.ERROR
    val log4jReporter = Log4jReporter(logger, logLevel)

    val df = mock[DataFrame]
    val dfName = "myDf"
    val numRows = 10
    val id = "a"

    val message1 = "1"
    val status1 = ConstraintSuccess
    val constraint1 = DummyConstraint(message1, status1)
    val result1 = constraint1.fun(df)

    val constraints = Map[Constraint, ConstraintResult[Constraint]](
      result1.constraint -> result1
    )
    val check = Check(df, Some(dfName), Option.empty, constraints.keys.toSeq, id)

    val date = new Date()
    log4jReporter.report(CheckResult(constraints, check, numRows))

    verify(logger).log(logLevel, s"""{"check" : {"id" : "$id", "time" : "$date", "name" : "$dfName", "rowsTotal" : $numRows}, "constraint" : "DummyConstraint", "status" : "Success", "message" : "$message1"}""")
  }

}