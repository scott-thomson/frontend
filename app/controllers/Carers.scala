package controllers

import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference
import scala.xml._
import org.cddcore.engine._
import org.joda.time._
import org.joda.time.format._
import org.junit.runner.RunWith
import org.cddcore.engine.Xml.boolean
import org.cddcore.engine.Xml.date
import org.cddcore.engine.Xml.double
import org.cddcore.engine.Xml.obj
import org.cddcore.engine.Xml.optionDate
import org.cddcore.engine.Xml.string
import org.cddcore.engine.Xml.xml
import org.cddcore.engine.Xml.yesNo
import org.cddcore.engine.tests.CddJunitRunner

object World {
  def apply(ninoToCis: NinoToCis): World = World(Xmls.asDate("2010-7-5"), ninoToCis)
}
case class World(dateProcessingDate: DateTime, ninoToCis: NinoToCis) extends LoggerDisplay {
  def loggerDisplay(dp: LoggerDisplayProcessor): String =
    "World(" + dateProcessingDate + ")"
  val maxWait = scala.concurrent.duration.Duration(1, "seconds")
}

trait NinoToCis {
  def apply(nino: String): Elem
}

class TestNinoToCis extends NinoToCis {
  def apply(nino: String) =
    try {
      val full = s"Cis/${nino}.txt"
      val url = getClass.getClassLoader.getResource(full)
      if (url == null)
        <NoCis/>
      else {
        val xmlString = scala.io.Source.fromURL(url).mkString
        val xml = XML.loadString(xmlString)
        xml
      }
    } catch {
      case e: Exception => throw new RuntimeException("Cannot load " + nino, e)
    }
}

case class KeyAndParams(key: String, params: Any*) {
  override def toString = "<" + key + params.mkString("(", ",", ")") + ">"
}

object Xmls {
  def validateClaim(id: String) = {
    try {
      val full = s"ValidateClaim/${id}.xml"
      val url = getClass.getClassLoader.getResource(full)
      val xmlString = scala.io.Source.fromURL(url).mkString
      val xml = XML.loadString(xmlString)
      xml
    } catch {
      case e: Exception => throw new RuntimeException("Cannot load " + id, e)
    }
  }
  /** The boolean is 'hospitalisation' */
  def validateClaimWithBreaks(breaks: (String, String, Boolean)*): CarersXmlSituation =
    validateClaimWithBreaksFull(breaks.map((x) => (x._1, x._2, if (x._3) "Hospitalisation" else "other", if (x._3) "Hospital" else "other")): _*)

  def validateClaimWithBreaksFull(breaks: (String, String, String, String)*): CarersXmlSituation = {
    val url = getClass.getClassLoader.getResource("ValidateClaim/CL801119A.xml")
    val xmlString = scala.io.Source.fromURL(url).mkString
    val breaksInCareXml = <ClaimBreaks>
                            {
                              breaks.map((t) =>
                                <BreakInCare>
                                  <BICFromDate>{ t._1 }</BICFromDate>
                                  <BICToDate>{ t._2 }</BICToDate>
                                  <BICReason>{ t._3 }</BICReason>
                                  <BICType>{ t._4 }</BICType>
                                </BreakInCare>)
                            }
                          </ClaimBreaks>
    val withBreaks = xmlString.replace("<ClaimBreaks />", breaksInCareXml.toString)
    new CarersXmlSituation(World(new TestNinoToCis), XML.loadString(withBreaks))
  }

  val formatter = DateTimeFormat.forPattern("yyyy-MM-dd");
  def asDate(s: String): DateTime = formatter.parseDateTime(s);
}

case class CarersXmlSituation(w: World, validateClaimXml: Elem) extends XmlSituation {
  //  override def htmlDisplay = "CarersXmlSituation"
  import Xml._
  lazy val claimStartDate = xml(validateClaimXml) \ "ClaimData" \ "ClaimStartDate" \ date
  lazy val timeLimitForClaimingThreeMonths = claimSubmittedDate().minusMonths(3)
  lazy val claimEndDate = xml(validateClaimXml) \ "ClaimData" \ "ClaimEndDate" \ optionDate
  lazy val claimSubmittedDate = xml(validateClaimXml) \ "StatementData" \ "StatementDate" \ date
  lazy val dependantAwardStartDate = xml(dependantCisXml) \ "Award" \ "AssessmentDetails" \ "ClaimStartDate" \ optionDate

  lazy val birthdate = xml(validateClaimXml) \ "ClaimantData" \ "ClaimantBirthDate" \ "PersonBirthDate" \ date

  lazy val claim35Hours = xml(validateClaimXml) \ "ClaimData" \ "Claim35Hours" \ yesNo(default = false)
  lazy val ClaimCurrentResidentUK = xml(validateClaimXml) \ "ClaimData" \ "ClaimCurrentResidentUK" \ yesNo(default = false)
  lazy val ClaimEducationFullTime = xml(validateClaimXml) \ "ClaimData" \ "ClaimEducationFullTime" \ yesNo(default = false)
  lazy val ClaimAlwaysUK = xml(validateClaimXml) \ "ClaimData" \ "ClaimAlwaysUK" \ yesNo(default = false)
  def underSixteenOn(date: DateTime) = birthdate.get() match {
    case Some(bd) => bd.plusYears(16).isAfter(date)
    case _ => false
  }
  lazy val DependantNino = xml(validateClaimXml) \ "DependantData" \ "DependantNINO" \ string
  lazy val dependantCisXml: Elem = DependantNino.get() match {
    case Some(s) => w.ninoToCis(s);
    case None => <NoDependantXml/>
  }
  lazy val dependantLevelOfQualifyingCare = xml(dependantCisXml) \\ "AwardComponent" \ string
  lazy val dependantHasSufficientLevelOfQualifyingCare = dependantLevelOfQualifyingCare() == "DLA Middle Rate Care"

  lazy val hasChildExpenses = xml(validateClaimXml) \ "ExpensesData" \ "ExpensesChild" \ yesNo(default = false)
  lazy val childExpensesAcount = xml(validateClaimXml) \ "ExpensesData" \ "ExpensesChildAmount" \ double
  lazy val hasPsnPension = xml(validateClaimXml) \ "ExpensesData" \ "ExpensesPsnPension" \ yesNo(default = false)
  lazy val psnPensionAcount = xml(validateClaimXml) \ "ExpensesData" \ "ExpensesPsnPensionAmount" \ double
  lazy val hasOccPension = xml(validateClaimXml) \ "ExpensesData" \ "ExpensesOccPension" \ yesNo(default = false)
  lazy val occPensionAcount = xml(validateClaimXml) \ "ExpensesData" \ "ExpensesOccPensionAmount" \ double
  lazy val hasEmploymentData = xml(validateClaimXml) \ "newEmploymentData" \ boolean
  lazy val employmentGrossSalary = xml(validateClaimXml) \ "EmploymentData" \ "EmploymentGrossSalary" \ double
  lazy val employmentPayPeriodicity = xml(validateClaimXml) \ "EmploymentData" \ "EmploymentPayPeriodicity" \ string
  lazy val breaksInCare = xml(validateClaimXml) \ "ClaimData" \ "ClaimBreaks" \ "BreakInCare" \
    obj((ns) => ns.map((n) => {
      val from = Xmls.asDate((n \ "BICFromDate").text)
      val to = Xmls.asDate((n \ "BICToDate").text)
      val reason = (n \ "BICType").text
      new DateRange(from, to, reason)
    }))

  lazy val nettIncome = Income.income(this) - Expenses.expenses(this)
  lazy val incomeTooHigh = nettIncome >= 110
  lazy val incomeOK = !incomeTooHigh

  private val guardConditionCache = new AtomicReference[Map[DateTime, Future[List[KeyAndParams]]]](Map())
  def guardConditions(dateTime: DateTime): List[KeyAndParams] = Maps.getOrCreate(guardConditionCache, dateTime, Carers.guardConditions(dateTime, this))

}

@RunWith(classOf[CddJunitRunner])
object Expenses {
  implicit def stringStringToCarers(x: String) = CarersXmlSituation(World(new TestNinoToCis), Xmls.validateClaim(x))

  val expenses = Engine.folding[CarersXmlSituation, Double, Double]((acc, v) => acc + v, 0).
    title("Expenses").
    code((c: CarersXmlSituation) => 0.0).
    childEngine("Child care expenses", """Customer's claiming CA may claim an allowable expense of up to 50% of their childcare expenses
        where the child care is not being undertaken by a direct relative. This amount may then be deducted from their gross pay.""").
    scenario("CL100110A").expected(15).
    because((c: CarersXmlSituation) => c.hasChildExpenses()).
    code((c: CarersXmlSituation) => c.childExpensesAcount() / 2).
    scenario("CL100104A").expected(0).

    childEngine("PSN  Pensions", """Customers claiming CA may claim an allowable expense of up to 50% of their Private Pension contributions. 
        This amount may then be deducted from their gross pay figure.""").
    scenario("CL100111A").expected(15).
    because((c: CarersXmlSituation) => c.hasPsnPension()).
    code((c: CarersXmlSituation) => c.psnPensionAcount() / 2).
    scenario("CL100104A").expected(0).

    childEngine("Occupational Pension",
      """Customers claiming CA may claim an allowable expense of up to 50% of their Occupational Pension contributions. 
        This amount may then be deducted from their gross pay figure.""").
      scenario("CL100112A").expected(15).
      because((c: CarersXmlSituation) => c.hasOccPension()).
      code((c: CarersXmlSituation) => c.occPensionAcount() / 2).
      scenario("CL100104A").expected(0).

      build
}

@RunWith(classOf[CddJunitRunner])
object Income {
  implicit def stringToCarers(x: String) = CarersXmlSituation(World(new TestNinoToCis), Xmls.validateClaim(x))
  implicit def stringToDate(x: String) = Xmls.asDate(x)
  implicit def stringStringToCarers(x: Tuple2[String, String]) = CarersXmlSituation(World(x._1, new TestNinoToCis), Xmls.validateClaim(x._2))

  val income = Engine[CarersXmlSituation, Double]().title("Income").
    useCase("No income", "A person without any income should return 0 as their income").
    scenario("CL100104A").expected(0).
    because((c: CarersXmlSituation) => !c.hasEmploymentData()).

    useCase("Annually paid", "A person who is annually paid has their annual salary divided by 52 to calculate their income").
    scenario("CL100113A").expected(7000.0 / 52).
    because((c: CarersXmlSituation) => c.employmentPayPeriodicity() == "Annually").
    code((c: CarersXmlSituation) => c.employmentGrossSalary() / 52).

    useCase("Weekly paid").
    scenario("CL100110A").expected(110).
    because((c: CarersXmlSituation) => c.employmentPayPeriodicity() == "Weekly").
    code((c: CarersXmlSituation) => c.employmentGrossSalary()).
    build
}

@RunWith(classOf[CddJunitRunner])
object Carers {
  implicit def stringToDate(x: String) = Xmls.asDate(x)
  implicit def stringStringToListDateAndString(x: (String, String)): List[(DateTime, String)] = List((Xmls.asDate(x._1), x._2))
  implicit def stringStringToDateRange(x: (String, String, String)) = DateRange(x._1, x._2, x._3)
  implicit def stringToCarers(x: String) = CarersXmlSituation(World(new TestNinoToCis), Xmls.validateClaim(x))
  implicit def toKeyAndParams(x: String) = Some(KeyAndParams(x))
  implicit def toValidateClaim(x: List[(String, String, Boolean)]): CarersXmlSituation = Xmls.validateClaimWithBreaks(x: _*)

  val carersPayment: Double = 110
  private def isInRange(dateOfInterest: DateTime, start: DateTime, end: Option[DateTime]) = {
    start.isBefore(dateOfInterest) &&
      end.isDefined &&
      end.get.isAfter(dateOfInterest)
  }

  private val addStartDateOfDateInInCareAndFirstDateOutOfBreak = (dr: DateRange) => List(
    (dr.from, "Break in care (" + dr.reason + ") started"),
    (dr.to.plusDays(1), "Break in care (" + dr.reason + ") ended"))

  private val addFirstDay = (dr: DateRange) => List(
    (dr.from, "Break in care (" + dr.reason + ") started"),
    (dr.to.plusDays(1), "Break in care (" + dr.reason + ") ended"))

  def conditionallyAddDate(dr: DateRange, weeks: Int): List[(DateTime, String)] = {
    val lastDay = dr.from.plusWeeks(weeks)
    if (dr.to.isAfter(lastDay) || dr.to == lastDay) List((lastDay, "Care break too long")) else List()
  }

  val interestingDates = Engine.folding[CarersXmlSituation, Iterable[(DateTime, String)], List[(DateTime, String)]]((acc, opt) => acc ++ opt, List()).title("Interesting Dates").
    childEngine("Sixteenth Birthday", "Your birthdate is interesting IFF you become the age of sixteen during the period of the claim").
    scenario("CL100105a").expected(List()).
    scenario("CL1PA100").expected(("2010-7-10", "Sixteenth Birthday")).
    code((c: CarersXmlSituation) => List((c.birthdate().plusYears(16), "Sixteenth Birthday"))).
    because((c: CarersXmlSituation) => isInRange(c.birthdate().plusYears(16), c.claimStartDate(), c.claimEndDate())).

    childEngine("Claim start date", "Is always an interesting date").
    scenario("CL100105a").expected(("2010-1-1", "Claim Start Date")).
    code((c: CarersXmlSituation) => List((c.claimStartDate(), "Claim Start Date"))).

    childEngine("Claim end date", "Is always an interesting date, and we have to fake it if it doesn't exist").
    scenario("CL100105a").expected(("3999-12-31", "Claim End Date")).

    scenario("CL1PA100").expected(("2999-12-31", "Claim End Date")).
    code((c: CarersXmlSituation) => List((c.claimEndDate().get, "Claim End Date"))).
    because((c: CarersXmlSituation) => c.claimEndDate().isDefined).

    childEngine("Claim submitted date", "Is always an interesting date").
    scenario("CL100105a").expected(("2010-1-1", "Claim Submitted Date")).
    code((c: CarersXmlSituation) => List((c.claimSubmittedDate(), "Claim Submitted Date"))).

    childEngine("Time Limit For Claiming Three Months", "Is an interesting date, if it falls inside the claim period").
    scenario("CL100105a").expected(List()).

    scenario("CL1PA100").expected(("2010-3-9", "Three month claim time limit")).
    code((c: CarersXmlSituation) => List((c.timeLimitForClaimingThreeMonths, "Three month claim time limit"))).
    because((c: CarersXmlSituation) => isInRange(c.timeLimitForClaimingThreeMonths, c.claimStartDate(), c.claimEndDate())).

    childEngine("Breaks in Care add the from date, and the first date after the to date").
    scenario(List(("2010-3-1", "2010-3-4", true)), "Single break").expected(List(("2010-3-1", "Break in care (Hospital) started"), ("2010-3-5", "Break in care (Hospital) ended"))).
    code((c: CarersXmlSituation) => c.breaksInCare().flatMap(addStartDateOfDateInInCareAndFirstDateOutOfBreak)).
    scenario(List(("2010-3-1", "2010-3-4", true), ("2010-4-1", "2010-4-4", true)), "Two breaks").
    expected(List(("2010-3-1", "Break in care (Hospital) started"), ("2010-3-5", "Break in care (Hospital) ended"),
      ("2010-4-1", "Break in care (Hospital) started"), ("2010-4-5", "Break in care (Hospital) ended"))).

    childEngine("Four weeks after the start of a non hospital break in care, and twelve weeks after a hospital break of care are interesting if the break is active then").
    scenario(List(("2010-7-1", "2010-7-4", false)), "Non hospital break, Too short").expected(List()).
    code((c: CarersXmlSituation) => c.breaksInCare().flatMap(dr => {
      dr.reason.equalsIgnoreCase("Hospital") match {
        case false => conditionallyAddDate(dr, 4)
        case true => conditionallyAddDate(dr, 12)
      }
    }).toList).
    scenario(List(("2010-7-1", "2010-8-1", false)), "Non hospital break, more than four weeks").expected(List(("2010-7-29", "Care break too long"))).
    scenario(List(("2010-7-1", "2010-8-1", true)), "Hospital break. Too short").expected(List()).
    scenario(List(("2010-7-1", "2010-10-1", true)), "Hospital break, more than twelve weeks").expected(List(("2010-9-23", "Care break too long"))).

    build;

  val singleBreakInCare = Engine[DateTime, DateTime, DateRange, Boolean]().title("Single Break In Care").
    description("The first date is the date being processed. The second date is the claim start date. The result is false if this DateRange invalidates the claim for the current date").
    useCase("Outside date range").
    scenario("2010-3-1", "2010-1-1", ("2010-5-1", "2010-5-5", "Reason"), "Before date").expected(true).
    because((processDate: DateTime, claimStartDate: DateTime, dr: DateRange) => !dr.contains(processDate)).

    useCase("Not yet 22 weeks after claim start date").
    scenario("2010-3-1", "2010-1-1", ("2010-3-1", "2010-3-5", "Reason"), "After date").expected(false).
    because((processDate: DateTime, claimStartDate: DateTime, dr: DateRange) => processDate.isBefore(claimStartDate.plusWeeks(22))).

    useCase(" 22 weeks after claim start date, non hospital").
    scenario("2010-7-1", "2010-1-1", ("2010-7-1", "2010-11-4", "Reason"), "Non hospital break, first day").expected(true).
    because((processDate: DateTime, claimStartDate: DateTime, dr: DateRange) => processDate.isBefore(dr.from.plusWeeks(4))).
    scenario("2010-7-2", "2010-1-1", ("2010-7-1", "2010-11-4", "Reason"), "Non hospital break, second day").expected(true).
    scenario("2010-7-28", "2010-1-1", ("2010-7-1", "2010-11-4", "Reason"), "Non hospital break, last day of four weeks").expected(true).
    scenario("2010-7-29", "2010-1-1", ("2010-7-1", "2010-11-4", "Reason"), "Non hospital break, first day after four weeks").expected(false).
    because((processDate: DateTime, claimStartDate: DateTime, dr: DateRange) => { val lastDay = dr.from.plusWeeks(4).minusDays(1); processDate.isAfter(lastDay) }).
    scenario("2010-11-4", "2010-1-1", ("2010-7-1", "2010-11-4", "Reason"), "Non hospital break, last day of break").expected(false).
    scenario("2010-11-5", "2010-1-1", ("2010-7-1", "2010-11-4", "Reason"), "Non hospital break, first day after break").expected(true).

    useCase(" 22 weeks after claim start date, hospital").
    scenario("2010-7-1", "2010-1-1", ("2010-7-1", "2010-12-4", "Hospital"), "Hospital break, first day").expected(true).
    scenario("2010-7-29", "2010-1-1", ("2010-7-1", "2010-11-4", "Hospital"), "Hospital break, first day after four weeks").expected(true).
    because((processDate: DateTime, claimStartDate: DateTime, dr: DateRange) => { val firstInvalidDay = dr.from.plusWeeks(12); dr.reason.equalsIgnoreCase("Hospital") && processDate.isBefore(firstInvalidDay) }).
    scenario("2010-09-21", "2010-1-1", ("2010-7-1", "2010-12-4", "Hospital"), "Hospital break, last day of twelve weeks 1").expected(true).
    scenario("2010-09-22", "2010-1-1", ("2010-7-1", "2010-12-4", "Hospital"), "Hospital break, last day of twelve weeks").expected(true).
    scenario("2010-09-23", "2010-1-1", ("2010-7-1", "2010-12-4", "Hospital"), "Hospital break, first day after twelve weeks").expected(false).
    scenario("2010-12-4", "2010-1-1", ("2010-7-1", "2010-12-4", "Hospital"), "Hospital break, last day of break").expected(false).
    scenario("2010-12-5", "2010-1-1", ("2010-7-1", "2010-12-4", "Hospital"), "Hospital break, first day after break").expected(true).
    build

  val breaksInCare = Engine[DateTime, CarersXmlSituation, Boolean]().title("Breaks in care").
    description("This works out if any given break in care (specified by the DateRange) still allows payment. For reference the validateClaimWithBreaks method " +
      "creates a validate claims application form with a claims start date of 2010-01-01. The 22 week enabler for breaks in care occurs on 2010-06-04").

    useCase("The datetime is outside any break in care, means that payment is OK").
    scenario("2010-05-12", List(("2010-5-13", "2010-6-13", true)), "Just before break").expected(true).
    code((d: DateTime, c: CarersXmlSituation) => {
      val startDate = c.claimStartDate()
      c.breaksInCare().foldLeft(true)((acc, dr) => acc && singleBreakInCare(d, startDate, dr))
    }).
    scenario("2010-06-14", List(("2010-5-13", "2010-6-13", true)), "Just after break").expected(true).
    scenario("2010-05-12", List(("2010-5-13", "2010-6-13", true), ("2010-6-1", "2010-6-2", true)), "Just before break, multiple breaks").expected(true).
    scenario("2010-06-14", List(("2010-5-13", "2010-6-13", true), ("2010-6-1", "2010-6-2", true)), "Just after break, multiple breaks").expected(true).

    useCase("The datetime is in a break in care (dependant in hospital), and the care payments were made for 22 weeks pre care, and the break is less than 12 weeks").
    scenario("2010-7-1", List(("2010-7-1", "2010-9-22", true)), "First day of break that is one day short of 12 weeks").expected(true).
    scenario("2010-9-22", List(("2010-7-1", "2010-9-22", true)), "Last day of break that is one day short of 12 weeks").expected(true).

    useCase("The datetime is in a break in care (dependant not in hospital), and the care payments were made for 22 weeks pre care, and the break is less than 4 weeks").
    scenario("2010-7-1", List(("2010-7-1", "2010-7-10", false)), "First day of break that is one day short of 4 weeks").expected(true).
    scenario("2010-7-10", List(("2010-7-1", "2010-7-10", false)), "Last day of break that is one day short of 4 weeks").expected(true).

    useCase("The datetime is in a break in care (dependant not in hospital), and the care payments were made for 22 weeks pre care, and the break is more than 4 weeks").
    scenario("2010-7-1", List(("2010-7-1", "2010-08-02", false)), "First day of break that is over 4 weeks").expected(true).
    scenario("2010-7-28", List(("2010-7-1", "2010-08-02", false)), "Last valid day of break that is over 4 weeks").expected(true).
    scenario("2010-7-29", List(("2010-7-1", "2010-08-02", false)), "First invalid day of break that is over 4 weeks").expected(false).
    scenario("2010-8-2", List(("2010-7-1", "2010-08-02", false)), "Last day of break that is over 4 weeks").expected(false).
    scenario("2010-8-3", List(("2010-7-1", "2010-08-02", false)), "After break that is over 4 weeks").expected(true).

    useCase("The datetime is in a break in care (dependant in hospital), and the care payments were made for 22 weeks pre care, and the break is more than 12 weeks").
    scenario("2010-6-04", List(("2010-6-4", "2010-9-1", true)), "First day of break that is over 12 weeks").expected(true).
    scenario("2010-8-25", List(("2010-6-4", "2010-9-1", true)), "Last valid day of break that is over 12 weeks1").expected(true).
    scenario("2010-8-26", List(("2010-6-4", "2010-9-1", true)), "Last valid day of break that is over 12 weeks").expected(true).
    scenario("2010-8-27", List(("2010-6-4", "2010-9-1", true)), "First invalid day of break that is over 12 weeks").expected(false).
    scenario("2010-9-1", List(("2010-6-4", "2010-9-1", true)), "Last day of break that is over 12 weeks").expected(false).
    scenario("2010-9-2", List(("2010-6-4", "2010-9-1", true)), "After break that is over 12 weeks").expected(true).

    useCase("The datetime is in a break in care and the care payments were not made for 22 weeks pre care").
    scenario("2010-2-2", List(("2010-3-1", "2010-3-3", true)), "before a break that is pre 22 weeks").expected(true).
    scenario("2010-3-1", List(("2010-3-1", "2010-3-3", true)), "first day of a break that is pre 22 weeks").expected(false).
    scenario("2010-3-3", List(("2010-3-1", "2010-3-3", true)), "last day a break that is pre 22 weeks").expected(false).
    scenario("2010-3-4", List(("2010-3-1", "2010-3-3", true)), "after a break that is pre 22 weeks").expected(true).
    build

  val guardConditions = Engine.folding[DateTime, CarersXmlSituation, Option[KeyAndParams], List[KeyAndParams]]((acc, opt) => acc ::: opt.toList, List()).title("Check Guard Condition").
    code((d: DateTime, c: CarersXmlSituation) => None).

    childEngine("Age Restriction", "Customers under age 16 are not entitled to Carers Allowance").
    scenario("2010-6-9", "CL100104A", "Cl100104A-Age Under 16").expected("carer.claimant.under16").
    because((d: DateTime, c: CarersXmlSituation) => c.underSixteenOn(d)).
    scenario("2022-3-1", "CL100104A", "Cl100104A-Age Under 16").expected(None).

    childEngine("Caring hours", "Customers with Hours of caring must be 35 hours or more in any one week").
    scenario("2010-1-1", "CL100105A", "CL100105A-lessThen35Hours").
    expected("carer.claimant.under35hoursCaring").
    because((d: DateTime, c: CarersXmlSituation) => !c.claim35Hours()).

    childEngine("Qualifying Benefit", "Dependant Party's without the required level of qualyfing benefit will result in the disallowance of the claim to Carer.").
    scenario("2010-6-23", "CL100106A", "CL100106A-Without qualifying benefit").
    expected(("carer.qualifyingBenefit.dpWithoutRequiredLevelOfQualifyingBenefit")).
    because((d: DateTime, c: CarersXmlSituation) => !c.dependantHasSufficientLevelOfQualifyingCare).

    childEngine("UK Residence", "Customer who is not considered resident and present in GB is not entitled to CA.").
    scenario("2010-6-7", "CL100107A", "CL100107A-notInGB").
    expected("carers.claimant.notResident").
    because((d: DateTime, c: CarersXmlSituation) => !c.ClaimAlwaysUK()).

    childEngine("Immigration Status", "Customers who have restrictions on their immigration status will be disallowed CA.").
    scenario("2010-6-7", "CL100108A", "CL100108A-restriction on immigration status").
    expected("carers.claimant.restriction.immigrationStatus").
    because((d: DateTime, c: CarersXmlSituation) => !c.ClaimCurrentResidentUK()).

    childEngine("Full Time Eduction", "Customers in Full Time Education 21 hours or more each week are not entitled to CA.").
    scenario("2010-2-10", "CL100109A", "CL100109A-full time education").
    expected("carers.claimant.fullTimeEduction.moreThan21Hours").
    because((d: DateTime, c: CarersXmlSituation) => c.ClaimEducationFullTime()).

    childEngine("High Salary", "Customers who earn more than the threshold value per week are not entitled to CA").
    scenario("2010-2-10", "CL100111A").expected(None).
    assertion((d: DateTime, c: CarersXmlSituation, optReason: ROrException[Option[KeyAndParams]]) => c.nettIncome == 95).

    scenario("2010-2-10", "CL100112A").expected(None).
    assertion((d: DateTime, c: CarersXmlSituation, optReason: ROrException[Option[KeyAndParams]]) => c.nettIncome == 95).

    scenario("2010-2-10", "CL100113A").expected("carers.income.tooHigh").
    because((d: DateTime, c: CarersXmlSituation) => c.incomeTooHigh).

    childEngine("Breaks in care (guard condition)", "Breaks in care may cause the claim to be invalid").
    scenario("2010-6-1", List(("2010-7-1", "2010-12-20", false)), "Long break in care, but date outside range").expected(None).
    scenario("2010-7-10", List(("2010-7-1", "2010-7-20", false)), "Short break in care when after 22 weeks").expected(None).
    scenario("2010-12-1", List(("2010-7-1", "2010-12-20", false)), "Long break in care when after 22 weeks").expected("carers.breakInCare").
    because((d: DateTime, c: CarersXmlSituation) => !breaksInCare(d, c)).
    build

  type ReasonsOrAmount = Either[Double, List[KeyAndParams]]
  implicit def toAmoumt(x: Double) = Left(x)
  implicit def toReasons(x: List[KeyAndParams]) = Right(x)
  implicit def stringsToReasons(x: List[String]) = Right(x.map(KeyAndParams(_)))

  val engine = Engine[DateTime, CarersXmlSituation, ReasonsOrAmount]().title("Validate Claim").
    code((d: DateTime, c: CarersXmlSituation) => Left(carersPayment)).
    useCase("Guard Conditions", "All guard conditions should be passed").
    scenario("2010-6-7", "CL100108A", "CL100108A-restriction on immigration status").
    expected(List("carers.claimant.notResident", "carers.claimant.restriction.immigrationStatus")).
    code((d: DateTime, c: CarersXmlSituation) => Right(c.guardConditions(d))).

    useCase("Employment 4", """Customer's claiming CA may claim an allowable expense of up to 50% of their childcare expenses where the child care is not being undertaken by a direct relative. 
          This amount may then be deducted from their gross pay.""").
    scenario("2010-3-22", "CL100110A", "CL100110A-child care allowance").
    expected(carersPayment).
    because((d: DateTime, c: CarersXmlSituation) => c.guardConditions(d).size == 0).

    useCase("Employment 5", """Customers claiming CA may claim an allowable expense of up to 50% of their Private Pension contributions. 
          This amount may then be deducted from their gross pay figure.""").
    scenario("2010-3-8", "CL100111A", "CL100111A-private pension").
    expected(carersPayment).

    useCase("Employment 6", """Customers claiming CA may claim an allowable expense of up to 50% of their Occupational Pension contributions. 
          This amount may then be deducted from their gross pay figure.""").
    scenario("2010-3-8", "CL100112A", "CL100112A-occupational pension").
    expected(carersPayment).

    build

  type TimeLineItem = (DateRangesToBeProcessedTogether, List[(DateRange, ReasonsOrAmount)])
  type TimeLine = List[TimeLineItem]
  /** Returns a DatesToBeProcessedTogether and the days that the claim is valid for */
  def findTimeLine(c: CarersXmlSituation): TimeLine = {
    val dates = interestingDates(c)
    val dayToSplit = DateRanges.sunday
    val result = DateRanges.interestingDatesToDateRangesToBeProcessedTogether(dates, dayToSplit)
    result.map((drCollection) => {
      val result =
        drCollection.dateRanges.map((dr) => {
          val result = engine(dr.from, c)
          (dr, result)
        })
      (drCollection, result)
    })
  }

  def startMain {
    val trace = Engine.trace(findTimeLine(Xmls.validateClaimWithBreaks(("2010-7-1", "2010-7-10", true))))
    val printer = TraceItem.print()_
    for (i <- trace._2)
      println(printer(i))
  }
  val loggerDisplayProcessor = LoggerDisplayProcessor(
    ClassFunction(classOf[CarersXmlSituation], (ldp: LoggerDisplayProcessor, c: CarersXmlSituation) => "CarersXmlSituation"),
    ClassFunction(classOf[List[_]], (ldp: LoggerDisplayProcessor, l: List[_]) => "List(" + l.map(ldp(_)).mkString(",") + ")"),
    ClassFunction(classOf[(DateRange, String)], (ldp: LoggerDisplayProcessor, ds: (DateRange, String)) => "(" + ldp(ds._1) + ",\"" + ds._2 + "\")"),
    ClassFunction(classOf[DateTime], (ldp: LoggerDisplayProcessor, d: DateTime) => DateRange.formatter.print(d)))

  def endMain {
    Engine.trace(findTimeLine(Xmls.validateClaimWithBreaks(("2010-7-1", "2010-7-10", true)))) //gentle warm up
    val trace = Engine.trace(findTimeLine(Xmls.validateClaimWithBreaks(("2010-7-1", "2010-7-10", true))))

    val result = trace._2.map((t: TraceItem) => Strings.oneLine(t.took + " " + t.toString(loggerDisplayProcessor))).mkString("\n")

    val printer = TraceItem.print(loggerDisplayProcessor)_
    for (i <- trace._2)
      println(printer(i))
    println
    println(trace._1.value.get.mkString("\n"))

  }
  def htmlMain {
    Engine.trace(findTimeLine(Xmls.validateClaimWithBreaks(("2010-7-1", "2010-7-10", true))))
    val trace = Engine.trace(findTimeLine(Xmls.validateClaimWithBreaks(("2010-7-1", "2010-7-10", true))))

    val result = trace._2.map((t: TraceItem) => Strings.oneLine(t.took + " " + t.toString(loggerDisplayProcessor))).mkString("\n")

    val printer = TraceItem.html(loggerDisplayProcessor)_
    println(printer(trace._2.toSeq))
    println
    println(trace._1.value.get.mkString("\n"))
    //    val file = new File("C:\\users\\phil\\Desktop\\test.html")
    //    Files.printToFile(file)((p) => p.print(printer(trace._2.toSeq)))
  }

  def main(args: Array[String]) {
    htmlMain 
  }

}