package controllers

import scala.language.implicitConversions
import org.junit.runner.RunWith
import org.joda.time.format.DateTimeFormat
import org.joda.time.DateTime
import org.cddcore.engine.Engine
import org.cddcore.engine.tests.CddJunitRunner
import org.joda.time.Days

object DateRange {
  val formatter = DateTimeFormat.forPattern("yyyy-MM-dd(E)");
  def datesMin(d1: DateTime, d2: DateTime) = if (d1.isAfter(d2)) d2 else d1
  def datesMax(d1: DateTime, d2: DateTime) = if (d1.isAfter(d2)) d1 else d2
}

/**
 * While a Date Range represents a period with a from and a to, one of it's primary jobs is to allow
 *  the operation of splitting it into three (which may well be identical) The first is from the from to the end of the first week
 *  the second is from the end of the first week to the start of the last week and the third is from the start of the last week to the to
 *  In all three cases the the dates returns will be clipped to 'from and to', so if the range is within a week for example the three ranges will be the same as 'this'
 *  The date ranges returned may be invalid!
 */
case class DateRange(val from: DateTime, val to: DateTime, val reason: String) {
  import DateRanges._
  import DateRange._
  val valid = to.isAfter(from) || to == from
  val dayOfFrom = from.dayOfWeek();
  val days = Days.daysBetween(from, to).getDays()
  def fromToEndOfFirstWeek(dayToSplit: Int) = DateRange(datesMax(from, firstDayOfWeek(from, dayToSplit)), datesMin(to, lastDayOfWeek(from, dayToSplit)), reason)
  def middleSection(dayToSplit: Int) = DateRange(firstDayOfWeek(from, dayToSplit).plusDays(7), firstDayOfWeek(to, dayToSplit).minusDays(1), reason)
  def startOfLastWeekToEnd(dayToSplit: Int) = DateRange(datesMax(from, firstDayOfWeek(to, dayToSplit)), to, reason)
  def contains(d: DateTime) = ((d == from) || d.isAfter(from)) && (d == to || d.isBefore(to))

  override def toString = s"DateRange($reason,${formatter.print(from)},${formatter.print(to)})"
}

/** The dateranges should be sorted */
case class DateRangesToBeProcessedTogether(dateRanges: List[DateRange]) {
  import Xmls._
  override def toString = s"DateRanges(start: ${formatter.print(dateRanges.head.from)}, end: ${formatter.print(dateRanges.last.to)}, all: ${dateRanges.mkString(",")}"
}

@RunWith(classOf[CddJunitRunner])
object DateRanges {
  val monday = 1
  val saturday = 6
  val sunday = 7

  implicit def stringToDate(x: String) = Xmls.asDate(x)
  implicit def stringStringToDateRange(x: Tuple3[String, String, String]) = DateRange(Xmls.asDate(x._1), Xmls.asDate(x._2), x._3)
  implicit def dateDateToDateRange(x: Tuple3[DateTime, DateTime, String]) = DateRange(x._1, x._2, x._3)
  implicit def listStringTuplesToDateRangesToBeProcessedTogether(stringTuples: List[Tuple3[String, String, String]]) =
    DateRangesToBeProcessedTogether(stringTuples.collect { case (from, to, reason) => DateRange(from, to, reason) })

  val firstDayOfWeek = Engine[DateTime, Int, DateTime]().title("First Day of Week").
    description("Given a date, and the day of the week that is day one, what is the date at the start of the week the date is in?").
    useCase("Day to split is less or equal than day of week").
    scenario("2010-1-4", 1, "monday split monday").expected("2010-1-4").
    code((d: DateTime, dayToSplit: Int) => d.withDayOfWeek(dayToSplit)).
    scenario("2010-1-5", 1, "tuesday split monday").expected("2010-1-4").
    scenario("2010-1-10", 1, "sunday split monday").expected("2010-1-4").
    scenario("2010-1-3", 7, "sunday split sunday").expected("2010-1-3").

    useCase("Day to split is bigger number than day of week").
    scenario("2010-1-5", 7, "tuesday split sunday").expected("2010-1-3").
    code((d: DateTime, dayToSplit: Int) => d.minusDays(7).withDayOfWeek(dayToSplit)).
    because((d: DateTime, dayToSplit: Int) => dayToSplit > d.getDayOfWeek()).

    scenario("2010-1-9", 7, "saturday split sunday").expected("2010-1-3").
    scenario("2010-1-4", 7, "monday split sunday").expected("2010-1-3").
    scenario("2010-1-10", 3, "monday split wednesday").expected("2010-1-6").
    build.cached

  def lastDayOfWeek(d: DateTime, dayToSplit: Int) = firstDayOfWeek(d, dayToSplit).plusDays(6)

  private def zipAllButLastAndAddLast = (dates: List[(DateTime, String)]) => {
    val d = dates.sortBy(_._1.getMillis).removeDuplicates
    val dWithoutLast = d.take(d.size - 1)
    val allButLast = dWithoutLast.zip(dWithoutLast.tail).collect { case (from, to) => DateRange(from._1, to._1.minusDays(1), from._2) }
    val last = DateRange(dWithoutLast.last._1, d.last._1, dWithoutLast.last._2)
    allButLast :+ last
  }

  def datesToRanges = Engine[List[(DateTime, String)], List[DateRange]]().title("Date to ranges").
    scenario(List(), "No days").expected(Nil).
    because((dates: List[(DateTime, String)]) => dates.size == 0).

    scenario(List(("2010-1-4", "token1")), "Just one day").expected(List(("2010-1-4", "2010-1-4", "token1"))).
    because((dates: List[(DateTime, String)]) => dates.size == 1).
    code((dates: List[(DateTime, String)]) => List(DateRange(dates.head._1, dates.head._1, dates.head._2))).

    scenario(List(("2010-1-4", "token1"), ("2010-1-10", "token2")), "two days").expected(List(("2010-1-4", "2010-1-10", "token1"))).
    because((dates: List[(DateTime, String)]) => dates.size > 1).
    code(zipAllButLastAndAddLast).
    scenario(List(("2010-1-4", "a"), ("2010-1-10", "b"), ("2010-2-1", "c")), "three days").
    expected(List(("2010-1-4", "2010-1-9", "a"), ("2010-1-10", "2010-2-1", "b"))).

    build

  private val groupByWeekRaw = (ranges: List[DateRange], dayToSplit: Int) =>
    ranges.groupBy((d) => {
      val fake = d.from.plusDays(8 - dayToSplit);
      fake.getYear + ":" + fake.weekOfWeekyear.getAsString
    }).toList.sortBy(_._1).collect { case (_, v) => DateRangesToBeProcessedTogether(v) }

  val groupByWeek = Engine[List[DateRange], Int, List[DateRangesToBeProcessedTogether]]().title("group by week").
    description("This is not intended to be called on its own. There is quite an onerous contract on the dates being passed in. They must either be an integer number of weeks starting on a dayToBeSplit, or contained within a week").
    useCase("Just one week").
    scenario(List(("2010-1-4", "2010-1-4", "aToken")), monday, "just monday, split monday").expected(List(List(("2010-1-4", "2010-1-4", "aToken")))).
    code(groupByWeekRaw).
    scenario(List(("2010-1-4", "2010-1-6", "a")), monday, "monday to wednesday, split monday").expected(List(List(("2010-1-4", "2010-1-6", "a")))).
    scenario(List(("2010-1-4", "2010-1-6", "a"), ("2010-1-7", "2010-1-9", "b")), monday, "monday to wednesday, thursday to saturday, split monday").expected(List(List(("2010-1-4", "2010-1-6", "a"), ("2010-1-7", "2010-1-9", "b")))).

    useCase("Multiple weeks").
    scenario(List(("2010-1-4", "2010-1-31", "a"), ("2010-2-1", "2010-2-3", "b")), monday, "monday to month wednesday, split monday").
    expected(List(List(("2010-1-4", "2010-1-31", "a")), List((("2010-2-1", "2010-2-3", "b"))))).
    scenario(List(("2010-1-4", "2010-1-6", "a"), ("2010-1-7", "2010-1-9", "b"), ("2010-1-11", "2010-1-31", "c"), ("2010-2-1", "2010-2-3", "d")), monday, "monday to wednesday, thursday to saturday, then monday to month wednesday split monday").
    expected(List(List(("2010-1-4", "2010-1-6", "a"), ("2010-1-7", "2010-1-9", "b")),
      List(("2010-1-11", "2010-1-31", "c")), List(("2010-2-1", "2010-2-3", "d")))).

    scenario(List(("2010-1-4", "2010-1-9", "a"), ("2010-1-10", "2010-1-30", "b"), ("2010-1-31", "2010-2-3", "c")), sunday, "monday to month wednesday, split sunday").
    expected(List(List(("2010-1-4", "2010-1-9", "a")), List((("2010-1-10", "2010-1-30", "b"))), List(("2010-1-31", "2010-2-3", "c")))).
    build

  def splitIntoStartMiddleEnd(ranges: List[DateRange], dayToSplit: Int): List[DateRange] = ranges.flatMap(splitIntoStartMiddleEnd(_, dayToSplit))
  val splitIntoStartMiddleEnd = Engine[DateRange, Int, List[DateRange]]().title("Splitting date ranges into start, middle and end. The second parameter is 'which day of the week is it using the usually Joda time definitions").
    useCase("Within same week").
    scenario(("2010-1-4", "2010-1-10", "a"), 1, "Monday to sunday, 1 week, split monday").expected(List(("2010-1-4", "2010-1-10", "a"))).
    code((x: DateRange, dayToSplit: Int) => {
      import DateRange._
      val raw =
        List[DateRange](
          x.fromToEndOfFirstWeek(dayToSplit),
          x.middleSection(dayToSplit),
          x.startOfLastWeekToEnd(dayToSplit))
      val result = raw.removeDuplicates.filter(_.valid)
      result
    }).
    scenario(("2010-1-4", "2010-1-5", "a"), monday, "Monday to Tuesday, split monday").expected(List(("2010-1-4", "2010-1-5", "a"))).
    scenario(("2010-1-5", "2010-1-9", "a"), monday, "Tuesday to Saturday, split monday").expected(List(("2010-1-5", "2010-1-9", "a"))).
    scenario(("2010-1-4", "2010-1-10", "a"), monday, "Monday to Sunday, split monday").expected(List(("2010-1-4", "2010-1-10", "a"))).
    scenario(("2010-1-4", "2010-1-5", "a"), sunday, "Monday to Tuesday, split sunday").expected(List(("2010-1-4", "2010-1-5", "a"))).
    scenario(("2010-1-5", "2010-1-9", "a"), sunday, "Tuesday to Saturday, split sunday").expected(List(("2010-1-5", "2010-1-9", "a"))).
    scenario(("2010-1-3", "2010-1-9", "a"), sunday, "Sunday to Saturday, split sunday").expected(List(("2010-1-3", "2010-1-9", "a"))).

    useCase("Across two weeks").
    scenario(("2010-1-6", "2010-1-12", "a"), monday, "Wednesday to Tuesday, split monday").expected(List(("2010-1-6", "2010-1-10", "a"), ("2010-1-11", "2010-1-12", "a"))).
    scenario(("2010-1-3", "2010-1-10", "a"), monday, "Sunday to Sunday, split monday").expected(List(("2010-1-3", "2010-1-3", "a"), ("2010-1-4", "2010-1-10", "a"))).
    scenario(("2010-1-2", "2010-1-9", "a"), monday, "Saturday to Saturday, split monday").expected(List(("2010-1-2", "2010-1-3", "a"), ("2010-1-4", "2010-1-9", "a"))).

    scenario(("2010-1-6", "2010-1-12", "a"), sunday, "Wednesday to Tuesday, split sunday").expected(List(("2010-1-6", "2010-1-9", "a"), ("2010-1-10", "2010-1-12", "a"))).
    scenario(("2010-1-2", "2010-1-9", "a"), sunday, "Saturday to Saturday, split sunday").expected(List(("2010-1-2", "2010-1-2", "a"), ("2010-1-3", "2010-1-9", "a"))).

    useCase("Across multiple weeks").
    scenario(("2010-1-4", "2010-2-2", "a"), monday, "Monday to Month Tuesday, split monday").expected(List(("2010-1-4", "2010-1-10", "a"), ("2010-1-11", "2010-1-31", "a"), ("2010-2-1", "2010-2-2", "a"))).
    scenario(("2010-1-6", "2010-2-2", "a"), monday, "Wednesday to Month Tuesday, split monday").expected(List(("2010-1-6", "2010-1-10", "a"), ("2010-1-11", "2010-1-31", "a"), ("2010-2-1", "2010-2-2", "a"))).
    scenario(("2010-1-3", "2010-2-7", "a"), monday, "Sunday to Month Sunday, split monday").expected(List(("2010-1-3", "2010-1-3", "a"), ("2010-1-4", "2010-1-31", "a"), ("2010-2-1", "2010-2-7", "a"))).
    scenario(("2010-1-2", "2010-2-6", "a"), monday, "Saturday to Month Saturday, split monday").expected(List(("2010-1-2", "2010-1-3", "a"), ("2010-1-4", "2010-1-31", "a"), ("2010-2-1", "2010-2-6", "a"))).

    scenario(("2010-1-4", "2010-2-2", "a"), sunday, "Monday to Month Tuesday, split sunday").expected(List(("2010-1-4", "2010-1-9", "a"), ("2010-1-10", "2010-1-30", "a"), ("2010-1-31", "2010-2-2", "a"))).
    scenario(("2010-1-6", "2010-2-2", "a"), sunday, "Wednesday to Month Tuesday, split sunday").expected(List(("2010-1-6", "2010-1-9", "a"), ("2010-1-10", "2010-1-30", "a"), ("2010-1-31", "2010-2-2", "a"))).
    scenario(("2010-1-3", "2010-2-7", "a"), sunday, "Sunday to Month Sunday, split sunday").expected(List(("2010-1-3", "2010-1-9", "a"), ("2010-1-10", "2010-2-6", "a"), ("2010-2-7", "2010-2-7", "a"))).
    scenario(("2010-1-3", "2010-2-6", "a"), sunday, "Sunday to Month Saturday, split sunday").expected(List(("2010-1-3", "2010-1-9", "a"), ("2010-1-10", "2010-1-30", "a"), ("2010-1-31", "2010-2-6", "a"))).
    scenario(("2010-1-2", "2010-2-6", "a"), sunday, "Saturday to Month Saturday, split sunday").expected(List(("2010-1-2", "2010-1-2", "a"), ("2010-1-3", "2010-1-30", "a"), ("2010-1-31", "2010-2-6", "a"))).

    build

  val interestingDatesToDateRangesToBeProcessedTogether = Engine[List[(DateTime, String)], Int, List[DateRangesToBeProcessedTogether]]().title("Interesting Dates To DateRanges To Be Processed Together").
    description("Given a list of interesting dates this puts them into date ranges, Splits them so that each date range is either an integer number of weeks, or inside a week, and then groups them by weeks").
    scenario(List(("2010-1-4", "a"), ("2010-1-6", "b"), ("2010-2-1", "c")), monday, "mon, wed, monday month, split monday").
    expected(List(List(("2010-1-4", "2010-1-5", "a"), ("2010-1-6", "2010-1-10", "b")), List(("2010-1-11", "2010-1-31", "b")), List(("2010-2-1", "2010-2-1", "b")))).
    code((dates: List[(DateTime, String)], dayToSplit: Int) => {
      val interesting = datesToRanges(dates)
      val split = splitIntoStartMiddleEnd(interesting, dayToSplit)
      val result = groupByWeek(split, dayToSplit)
      result
    }).
    scenario(List(("2010-1-4", "a"), ("2010-1-6", "b"), ("2010-2-1", "c")), sunday, "mon, wed, monday month, split sunday").
    expected(List(List(("2010-1-4", "2010-1-5", "a"), ("2010-1-6", "2010-1-9", "b")), List(("2010-1-10", "2010-1-30", "b")), List(("2010-1-31", "2010-2-1", "b")))).

    build

  def main(args: Array[String]) {
    implicit def stringToDate(x: String) = Xmls.asDate(x)
    val t: List[(DateTime, String)] = List(("2010-1-3", "a"), ("2010-1-5", "b"), ("2010-1-6", "c"), ("2010-2-10", "d"));
    val dayToSplit = DateRanges.sunday
    val result = DateRanges.interestingDatesToDateRangesToBeProcessedTogether(t, dayToSplit)
    println(result.mkString("\n"))
    println
  }
}
