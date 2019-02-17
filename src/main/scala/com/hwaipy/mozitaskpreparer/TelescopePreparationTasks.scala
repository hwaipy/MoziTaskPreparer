package com.hwaipy.mozitaskpreparer

import java.awt.image.BufferedImage
import java.io._
import java.nio.file.{Files, StandardCopyOption}
import java.time.format.DateTimeFormatter
import java.time.{Duration, LocalDate, LocalDateTime, LocalTime}
import javax.imageio.ImageIO
import com.xeiam.xchart.{ChartBuilder, SeriesMarker, StyleManager}
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.CellType
import com.hwaipy.hydrogen.physics.polarization.muellermatrix._
import collection.JavaConverters._
import scala.io.Source
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import scala.collection.mutable.{ArrayBuffer, ListBuffer}

object TelescopePreparationTasks {
  def generatePreparationTask(basePath: File, parameters: Map[String, String] = Map()) = {
    val modeLZX = parameters("Mode").toBoolean
    val trackingTraceTask = TelescopePreparationTasks.generateTrackingTraceTask(basePath)
    val polarizationControlTask = TelescopePreparationTasks.generatePolarizationControlTask(basePath, parameters)
    val waveplateCalculateTask = TelescopePreparationTasks.generateWaveplateCalculateTask(basePath, parameters)
    new SerialTask(List(trackingTraceTask, polarizationControlTask, waveplateCalculateTask))
  }

  //run1
  private def generateTrackingTraceTask(basePath: File) = new Task {
    private val times = List(100, 2000, 5000)
    progressUpdate(0, times.sum)

    override def run: Unit = {
      val fileDQJH = new File(basePath, "短期计划").listFiles(new FileFilter {
        override def accept(f: File): Boolean = f.isDirectory && f.getName.endsWith("LJ")
      }).head.listFiles(new FileFilter {
        override def accept(f: File): Boolean = f.getName.toLowerCase.endsWith(".dat")
      }).head
      val planPath = new File(basePath, "plan")
      planPath.mkdirs
      progressUpdate(times.slice(0, 1).sum)
      traceDQJH(planPath, fileDQJH)
    }

    def traceDQJH(basePath: File, DQJHFile: File) {
      val resultDir = new File(basePath, "DQJH/")
      clean(resultDir)
      resultDir.mkdirs
      DQJH2ReMap(DQJHFile, resultDir)
      progressUpdate(times.slice(0, 2).sum)
      remap2JHJ(resultDir)
      progressUpdate(times.slice(0, 3).sum)
    }

    def DQJH2ReMap(traceSrc: File, resultDir: File) {
      val cd2dpPath = new File("tools/CD2DP_6_for_TG2_v1.0")
      val traceTarget = new File(cd2dpPath, "DQJH.dat")
      Files.copy(traceSrc.toPath, traceTarget.toPath, StandardCopyOption.REPLACE_EXISTING)
      if (System.getProperty("os.name").toLowerCase.contains("mac")) {
        Files.copy(traceSrc.getParentFile.getParentFile.getParentFile.toPath.resolve("help").resolve("R_DQJH.Dat"), new File(cd2dpPath, "R_DQJH.dat").toPath, StandardCopyOption.REPLACE_EXISTING)
      } else {
        val process = Runtime.getRuntime.exec(s"${new File(cd2dpPath, "CD2DP_6_for TG2_v1.0.exe").getAbsolutePath}", Array[String](), cd2dpPath)
        process.waitFor
      }
      val remapDir = new File(resultDir, "ReMap")
      remapDir.mkdirs
      Files.move(new File(cd2dpPath, "R_DQJH.dat").toPath, new File(remapDir, "R_DQJH.dat").toPath, StandardCopyOption.REPLACE_EXISTING)
      traceTarget.delete
      new File(cd2dpPath, "R_DQJH.dat").delete
      val pw = new PrintWriter(new File(remapDir, "ReMap.dat"), "GB2312")
      val pwangle = new PrintWriter(new File(remapDir, "ReMap.angle"), "GB2312")
      pw.println(" ===========================================================\n 量子卫星星历转跟踪文件计算软件\n 版    本 ：J2K2CZ V1.1\n 发布日期 ：2016-08-14\n 文件描述 ：所有符合计算要求的输出数据\n 作者Email：wjf@nao.cas.cn\n -----------------------------------------------------------\n测站:LJZ: 100  1 45.432    26 41 38.376   3227.00                          \n ===========================================================\n编号  年   月 日 时  分    秒     X(km)     Y(km)     Z(km)    斜距(km)    赤经(度)    赤纬(度)  方位角(度)  高度角(度)  相位角(度)   太阳高角    高程(km) 地影标识\n-------------------------------------------------------------------------------------------------------------------------------------------------------------------")
      val source = Source.fromFile(new File(remapDir, "R_DQJH.dat"))
      source.getLines.foreach(line => {
        val items = line.split(" +").toList
        pw.println(s"${items(0)}  ${items(1).substring(0, 4).toInt}   ${items(1).substring(4, 6).toInt}   ${items(1).substring(6, 8).toInt}  ${items(2).substring(0, 2).toInt}  ${items(2).substring(2, 4).toInt}   ${items(2).substring(4, 6).toInt}.${items(2).substring(6, 9)}     ${items(7)}    0   0  ${items(3)}   ${items(5)}     0  0    0")
        pwangle.println(s"${items(3)},${items(5)}")
      })
      pw.close()
      pwangle.close()
      source.close()
    }

    def remap2JHJ(resultDir: File) {
      val remapFile = new File(resultDir, "ReMap/ReMap.dat")
      val mfcPath = new File("tools/ReMap2JHJ")
      val inMFCRemapFile = new File(mfcPath, "ReMap.dat")
      Files.copy(remapFile.toPath, inMFCRemapFile.toPath, StandardCopyOption.REPLACE_EXISTING)
      mfcPath.listFiles.filter(file => file.getName.toUpperCase.endsWith("JHJ") || file.getName.toUpperCase.endsWith("JHD")).foreach(_.delete)
      if (System.getProperty("os.name").toLowerCase.contains("mac")) {
        val helpPath = resultDir.getParentFile.getParentFile.toPath.resolve("help")
        Files.list(helpPath).iterator().asScala.toList.filter(path => path.toString.toLowerCase.endsWith("jhj") || path.toString.toLowerCase.endsWith("jhd"))
          .foreach(path => Files.copy(path, new File(mfcPath, path.getFileName.toString).toPath, StandardCopyOption.REPLACE_EXISTING))
      } else {
        val process = Runtime.getRuntime.exec(s"${new File(mfcPath, "ReMapToJHJMFC.exe").getAbsolutePath}", Array[String](), mfcPath)
        process.waitFor
      }
      val jhjPath = new File(resultDir, "JHJ")
      jhjPath.mkdirs
      mfcPath.listFiles.filter(file => file.getName.toUpperCase.endsWith("JHJ") || file.getName.toUpperCase.endsWith("JHD"))
        .foreach(src => Files.move(src.toPath, new File(jhjPath, src.getName).toPath))
      inMFCRemapFile.delete
      val date = basePath.getName
      new File(jhjPath, "LIJIANG.JHJ").renameTo(new File(jhjPath, s"1000_7001_${date}_0001.JHF_${date}.JHJ"))
    }
  }

  //run2
  private def generatePolarizationControlTask(basePath: File, parameters: Map[String, String] = Map()) = new Task {

    import ExperimentRecord._
    import Angle._

    progressUpdate(0, 1000)
    val satellitePhase = parameters.getOrElse("Phase Satellite", "60").toDouble

    override def run: Unit = {
      val DQJHPath = new File(basePath, "短期计划").listFiles(new FileFilter {
        override def accept(f: File): Boolean = f.isDirectory && f.getName.endsWith("LJ")
      }) match {
        case ps if ps.size == 1 => ps.head
        case _ => throw new RuntimeException("短期计划文件错误")
      }
      val resultPath = new File(basePath, "result")
      val polComposePath = new File(basePath, "偏振补偿")
      resultPath.mkdirs
      polComposePath.mkdirs
      val tracePlan = GeneralRecord.fromCSVFile(new File(new File(basePath, "plan"), "DQJH/ReMap/ReMap.dat"), "GB2312").asTracePlan
      val workbook = new HSSFWorkbook()
      val sheet = workbook.createSheet("Polarization Compose")
      val headRow = List("方位", "俯仰", "卫星姿态", "JF-A", "JF-E", "姿态补偿", "时间", "时刻", "补偿相位", "额外HWP")
      Range(0, tracePlan.items.size + 1).foreach(r => {
        val row = sheet.createRow(r)
        Range(0, headRow.size).foreach(c => row.createCell(c))
      })
      headRow.zipWithIndex.foreach(zip => sheet.getRow(0).getCell(zip._2).setCellValue(zip._1))

      val attitudeSatelliteFile = DQJHPath.listFiles(new FileFilter {
        override def accept(f: File): Boolean = f.getName.contains("LJZ基矢810T") && f.getName.toLowerCase.endsWith(".csv")
      }).head
      val attitudeSatellite = GeneralRecord.fromCSVFile(attitudeSatelliteFile, "GB2312").asAttitudeSatelliteList
      val AESatelliteFiles = DQJHPath.listFiles(new FileFilter {
        override def accept(f: File): Boolean = f.getName.contains("双站模式") && f.getName.contains("量子纠缠发射机") && f.getName.contains("引导曲线") && f.getName.toLowerCase.endsWith(".xlsx")
      })
      val AESatellite = AESatelliteFiles.size match {
        case 0 => new ExperimentRecord(LocalDateTime.now, (0 to tracePlan.items.size).toList.map(i => new RecordItem(LocalDateTime.now, LocalDateTime.now, Some(new TelescopeStatus(0.asDegree, 0.asDegree)))))
        case _ => GeneralRecord.fromXLSFile(AESatelliteFiles.head).asAESatellite
      }

      (0 until tracePlan.items.size).foreach(i => {
        val row = sheet.getRow(i + 1)
        row.getCell(0).setCellValue(tracePlan.items(i).telescopeStatus.get.azimuth.degree)
        row.getCell(1).setCellValue(tracePlan.items(i).telescopeStatus.get.elevation.degree)
        row.getCell(2).setCellValue(attitudeSatellite(i + 1))
        row.getCell(3).setCellValue(AESatellite.items(i + 1).telescopeStatus.get.azimuth.degree)
        row.getCell(4).setCellValue(AESatellite.items(i + 1).telescopeStatus.get.elevation.degree)
        row.getCell(5).setCellType(CellType.FORMULA)
        row.getCell(5).setCellFormula(s"C${i + 2}-(E${i + 2}-D${i + 2})")
        row.getCell(6).setCellValue(i + 1)
        row.getCell(7).setCellValue(tracePlan.items(i).time.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
        row.getCell(8).setCellValue(satellitePhase)
        row.getCell(9).setCellValue(0)
      })
      val fileOutputStream = new FileOutputStream(new File(polComposePath, "偏振补偿.xls"))
      workbook.write(fileOutputStream)
      fileOutputStream.close

      progressUpdate(1000)
    }
  }

  //run3
  private def generateWaveplateCalculateTask(basePath: File, parameters: Map[String, String] = Map()) = new Task {
    progressUpdate(0, 42000)

    val telescopeOriginAzimuth = parameters.getOrElse("TelescopeOriginAzimuth", "32.75001").toDouble
    val phase1 = parameters.getOrElse("Phase 1", "0.38001").toDouble
    val phase2 = parameters.getOrElse("Phase 2", "-1.33001").toDouble
    val phase3 = parameters.getOrElse("Phase 3", "0.40001").toDouble

    override def run: Unit = {
      val file = new File(basePath, "偏振补偿/偏振补偿.xls")
      val angles = loadAngles(file)
      val results = new ArrayBuffer[Array[Double]]()
      angles.foreach(angle => {
        val rH = -(angle(0) - telescopeOriginAzimuth) / 180 * math.Pi
        val rV = -angle(1) / 180 * math.Pi
        val rotate = angle(2) / 180 * Math.PI
        val phase = angle(3) / 180 * Math.PI
        val bbMatrix = TelescopeTransform.create(phase1, phase2, phase3, rV, rH, phase, rotate)
        val comp = M1Process.calculate(bbMatrix).toArray
        results += comp
      })
      progressUpdate(1000)
      val contResults = ArrayBuffer[Array[Double]](results.head)
      val annealingResults = ArrayBuffer[Double]()
      (1 until results.size).foreach(i => {
        val angleLast = contResults(i - 1)
        val angleCurrent = results(i)
        val annealingResult = new AnnealingProcess(0.9999, 4048, angleLast, angleCurrent).process
        contResults += annealingResult._1
        annealingResults += annealingResult._2
        progressUpdate(1000 + 40000 * i / results.size)
      })
      (0 until contResults.size).foreach(i => {
        val HWP = angles(i)(4) / 180.0 * Math.PI
        contResults(i)(2) = contResults(i)(2) + HWP
      })
      val outputFile = new File(file.getAbsolutePath() + ".csv")
      val data = (0 until 6).toList.map(_ => new ArrayBuffer[Double]())
      val printWriter = new PrintWriter(outputFile)
      (0 until contResults.size).foreach(i => {
        val zeros = List(-14.471, +51.315, -15.865)
        val wpAngles = contResults(i).zip(zeros).map(z => z._1 / Math.PI * 180 + z._2)
        printWriter.println(f"$i,${wpAngles(0)}%.3f,${wpAngles(1)}%.3f,${wpAngles(2)}%.3f")
        data(0) += i
        data(1) += wpAngles(0)
        data(2) += wpAngles(1)
        data(3) += wpAngles(2)
        data(4) += angles(i)(0)
        data(5) += angles(i)(1)
      })
      printWriter.close
      val chartData = data.map(_.toArray)
      val chart = new ChartBuilder().width(1024).height(800).chartType(StyleManager.ChartType.Line).title("望远镜/小转台角度曲线").xAxisTitle("Time (s)").yAxisTitle("Angles").build()
      val s4 = chart.addSeries("望远镜方位", chartData(0), chartData(4))
      val s5 = chart.addSeries("望远镜俯仰", chartData(0), chartData(5))
      val s1 = chart.addSeries("QWP1", chartData(0), chartData(1))
      val s2 = chart.addSeries("QWP2", chartData(0), chartData(2))
      val s3 = chart.addSeries("HWP", chartData(0), chartData(3))
      chart.getStyleManager().setMarkerSize(0)
      val image = new BufferedImage(1024, 800, BufferedImage.TYPE_INT_ARGB)
      val g2 = image.createGraphics()
      chart.paint(g2)
      g2.dispose()
      ImageIO.write(image, "png", new File(file.getAbsolutePath() + ".png"))

      val pwLog = new PrintWriter(new File(file.getAbsolutePath() + ".log"))
      annealingResults.foreach(pwLog.println)
      pwLog.close

      progressUpdate(42000)
    }

    private def loadAngles(file: File) = {
      val is = new FileInputStream(file)
      val workbook = new HSSFWorkbook(is)
      val sheet = workbook.getSheetAt(0)
      val angles = new ListBuffer[List[Double]]()
      val hasTomoQWP = false
      (1 to sheet.getLastRowNum()).foreach(i => {
        val row = sheet.getRow(i)
        val A = row.getCell(0).getNumericCellValue()
        val E = row.getCell(1).getNumericCellValue()
        val AS = row.getCell(2).getNumericCellValue() - (row.getCell(4).getNumericCellValue() - row.getCell(3).getNumericCellValue())
        val phase = row.getCell(8).getNumericCellValue()
        val HWP = row.getCell(9).getNumericCellValue()
        angles += List(A, E, AS, phase, HWP)
      })
      angles.toList
    }
  }

  private def clean(file: File) {
    if (file.isDirectory) file.listFiles.foreach(clean)
    if (file.exists) file.delete
  }
}

object GeneralRecord {
  def fromCSVFile(file: File, encoding: String = "UTF-8") =
    new GeneralRecord(Source.fromFile(file, encoding).getLines.toList.map(line => {
      line.split("[ \t,]+").filter(s => s.length > 0).toList
    }))

  def fromXLSFile(file: File) = {
    val sheet = new XSSFWorkbook(file).getSheetAt(0)
    new GeneralRecord((sheet.getFirstRowNum to sheet.getLastRowNum).map(rownum => {
      val row = sheet.getRow(rownum)
      (row.getFirstCellNum to row.getLastCellNum).map(cellnum => {
        row.getCell(cellnum) match {
          case cell if cell == null => "null"
          case cell => cell.getRawValue
        }
      }).toList
    }).toList)
  }
}

class GeneralRecord private(val content: List[List[String]]) {
}

object ExperimentRecord {

  import Angle._

  implicit class GeneralRecordImp(gr: GeneralRecord) {
    def asTracePlan = {
      val valid = gr.content.drop(11)

      def toTime(list: List[String]) = {
        LocalDateTime.of(list(1).toInt, list(2).toInt, list(3).toInt, list(4).toInt, list(5).toInt, list(6).toDouble.toInt).plusHours(8)
      }

      val startTime = toTime(valid.head)

      def toRecordItem(list: List[String]) = {
        val time = toTime(list)
        val telescope = new TelescopeStatus(list(10).toDouble.asDegree, list(11).toDouble.asDegree)
        new RecordItem(startTime, time, telescopeStatus = Some(telescope))
      }

      new ExperimentRecord(startTime, valid.map(toRecordItem))
    }

    def asZhukongRecord(startTime: LocalDateTime) = {
      val valid = gr.content.drop(5)
      val refTime = startTime.minusHours(8).toLocalDate.atStartOfDay

      def toRecordItem(list: List[String]) = {
        val timeOfDay = LocalTime.of(list(1).toInt, list(2).toInt, list(3).toInt, list(4).toInt * 1000000)
        val time = refTime.plusNanos(timeOfDay.toNanoOfDay).plusHours(8)
        val telescope = new TelescopeStatus(list(10).toDouble.asDegree, list(9).toDouble.asDegree)
        new RecordItem(startTime, time, telescopeStatus = Some(telescope))
      }

      new ExperimentRecord(startTime, valid.map(line => toRecordItem(line)))
    }

    def asTelescopeRecord(startTime: LocalDateTime) = {
      val valid = gr.content.drop(1)
      val refTime = startTime.minusHours(8).toLocalDate.atStartOfDay

      def toRecordItem(list: List[String]) = {
        val nanoOfDay = (list(2).toDouble * 1000000000).toLong
        val time = refTime.plusNanos(nanoOfDay).plusHours(8)
        val telescope = new TelescopeStatus(list(3).toDouble.asDegree, list(4).toDouble.asDegree)
        new RecordItem(startTime, time, telescopeStatus = Some(telescope))
      }

      new ExperimentRecord(startTime, valid.map(line => toRecordItem(line)))
    }

    def asZongkongCountsRecord(startTime: LocalDateTime) = {
      val refTime = startTime.minusHours(8).toLocalDate.atStartOfDay

      def toRecordItem(list: List[String]) = {
        val milli = math.round(list(0).toDouble * 3600 * 1000)
        val time = refTime.plusNanos((milli * 1000000)).plusHours(8)
        val counts = new Counts(list(6).toInt, list(7).toInt, list(8).toInt, list(9).toInt, list(12).toInt, list(15).toInt, list(18).toInt, list(17).toInt, list(14).toInt)
        new RecordItem(startTime, time, counts = Some(counts))
      }

      new ExperimentRecord(startTime, gr.content.map(line => toRecordItem(line)))
    }

    def asZongkongRecord(startTime: LocalDateTime) = {
      val refTime = startTime.minusHours(8).toLocalDate.atStartOfDay

      def toRecordItem(list: List[String]) = {
        val milli = math.round(list(0).toDouble * 3600 * 1000)
        val time = refTime.plusNanos((milli * 1000000)).plusHours(8)
        val qqh = new QQHStatus(list(20).toDouble.asDegree, list(21).toDouble.asDegree, list(22).toDouble.asDegree)
        new RecordItem(startTime, time, qqhStatus = Some(qqh))
      }

      new ExperimentRecord(startTime, gr.content.map(line => toRecordItem(line)))
    }

    def asTracePlanDMZC = {
      def toTime(list: List[String]) = {
        LocalDateTime.of(list(0).toInt, list(1).toInt, list(2).toInt, list(3).toInt, list(4).toInt, list(5).toInt).plusHours(8)
      }

      val startTime = toTime(gr.content.head)

      def toRecordItem(list: List[String]) = {
        val time = toTime(list)
        val telescope = new TelescopeStatus(list(6).toDouble.asDegree, list(7).toDouble.asDegree)
        new RecordItem(startTime, time, telescopeStatus = Some(telescope))
      }

      new ExperimentRecord(startTime, gr.content.map(toRecordItem))
    }

    def asAttitudeSatelliteList = gr.content.map(i => i(1).toDouble)

    def asAESatellite = {
      def toTime(list: List[String]) = {
        LocalDateTime.of(list(0).toInt, list(1).toInt, list(2).toInt, list(3).toInt, list(4).toInt, list(5).toInt).plusHours(8)
      }

      val startTime = toTime(gr.content.head)

      def toRecordItem(list: List[String]) = {
        val time = toTime(list)
        val telescope = new TelescopeStatus(list(6).toDouble.asDegree, list(7).toDouble.asDegree)
        new RecordItem(startTime, time, telescopeStatus = Some(telescope))
      }

      new ExperimentRecord(startTime, gr.content.map(toRecordItem))
    }
  }

}

class ExperimentRecord(val startTime: LocalDateTime, private val contents: List[RecordItem]) {
  val items = contents.sortWith((a, b) => a.secondFromStart < b.secondFromStart)

  def chart(width: Int = 800, height: Int = 600, title: String = "", xAxisTitle: String = "Time (s)", yAxisTitle: String = "Degree (°)", serialMap: Map[String, RecordItem => Double] = Map()) = {
    val chart = new ChartBuilder().width(width).height(height).title(title).theme(StyleManager.ChartTheme.Matlab).build
    serialMap.foreach(s => {
      val serialName = s._1
      val serialOperator = s._2
      val xs = items.map(item => item.secondFromStart)
      val ys = items.map(item => serialOperator(item))
      val series = chart.addSeries(serialName, xs.toArray, ys.toArray)
      series.setMarker(SeriesMarker.NONE)
    })
    chart.getStyleManager.setLegendPosition(StyleManager.LegendPosition.InsideNE)
    chart.setXAxisTitle(xAxisTitle)
    chart.setYAxisTitle(yAxisTitle)
    val bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g2 = bi.createGraphics
    chart.paint(g2)
    g2.dispose
    bi
  }

  def chartToFile(width: Int = 800, height: Int = 600, name: String, date: LocalDate, stationName: String = "丽江站", path: File, serialMap: Map[String, RecordItem => Double] = Map()) = {
    val title = s"$date $stationName $name"
    val file = new File(path, s"$title.png")
    val bi = chart(width, height, title, serialMap = serialMap)
    ImageIO.write(bi, "png", file)
  }
}

class TelescopeStatus(val azimuth: Angle, val elevation: Angle) {
  def spatialDiff(ts: TelescopeStatus) = {
    import math._
    import Angle._
    val dA = (azimuth - ts.azimuth).arc match {
      case da if da > Pi => da - 2 * Pi
      case da if da < -Pi => da + 2 * Pi
      case da => da
    }
    val dE = (elevation - ts.elevation).arc
    val dAp = cos(elevation.arc) * dA
    sqrt(pow(dE, 2) + pow(dAp, 2)).asArc
  }

  override def toString: String = f"Telescope(${azimuth.degree}%1.1f°, ${elevation.degree}%1.1f°)"
}

class Angle(val arc: Double) {
  val degree = arc / math.Pi * 180

  def -(a: Angle) = new Angle(arc - a.arc)

  override def toString: String = s"$arc arc"
}

object Angle {

  implicit class DoubleImp(d: Double) {
    def asArc = {
      new Angle(d)
    }

    def asDegree = {
      new Angle(d / 180.0 * math.Pi)
    }
  }

  implicit class IntImp(d: Int) {
    def asArc = {
      new Angle(d)
    }

    def asDegree = {
      new Angle(d / 180.0 * math.Pi)
    }
  }

}

class RecordItem(val startTime: LocalDateTime, val time: LocalDateTime, val telescopeStatus: Option[TelescopeStatus] = None, val qqhStatus: Option[QQHStatus] = None, val counts: Option[Counts] = None) {
  val secondFromStart = Duration.between(startTime, time).toMillis / 1000.0

  override def toString: String = {
    val showItems = (s"${secondFromStart.toString} s") :: (if (qqhStatus.isDefined) qqhStatus.get.toString else "") :: (if (telescopeStatus.isDefined) telescopeStatus.get.toString else "") :: (if (counts.isDefined) counts.get.toString else "") :: Nil
    val showContent = showItems.filter(_.length > 0).mkString(", ")
    s"RecordItem[$showContent]"
  }
}

class QQHStatus(val q1: Angle, val q2: Angle, val h: Angle) {
  override def toString: String = f"QQH(${q1.degree}%1.1f°, ${q2.degree}%1.1f°, ${h.degree}%1.1f°)"
}

class Counts(val gps: Int, val c810A: Int, val c810B: Int, val s850: Int, val s532: Int, val cH: Int, val cV: Int, val cD: Int, val cA: Int) {
  val contrastHV = cH.toDouble / cV
  val contrastDA = cD.toDouble / cA
  val c810sum = c810A + c810B

  override def toString: String = f"Counts(GPS: $gps, 810 nm: ${c810A + c810B}, sync 850 nm: $s850, sync 532 nm: $s532; ContrastHV: $contrastHV%1.3f, ContrastDA: $contrastDA%1.3f)"
}

object TelescopeTransform {
  def create(PA: Double, PB: Double, PC: Double, RV: Double, RH: Double, phase: Double, rotate: Double) = MuellerMatrix.merge(
    new Phase(phase),
    new Rotate(rotate),
    new WavePlate(PA, 0),
    new Rotate(RV),
    new WavePlate(PB, 0),
    new Rotate(RH),
    new WavePlate(PC, 0)
  )
}