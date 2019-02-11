package com.hwaipy.mozitaskpreparer

import java.io.{File, FileFilter, PrintWriter}
import java.nio.file.{Files, StandardCopyOption}
import java.util.concurrent.atomic.AtomicInteger
import collection.JavaConverters._
import scala.io.Source
import org.apache.poi.xssf.usermodel.XSSFWorkbook

object TelescopePreparationTasks {
  //run1
  def generateTrackingTraceTask(basePath: File) = new Task {
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
      //      calculateWPAs(resultDir)
      //      progressUpdate(times.slice(0, 4).sum)
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
      Source.fromFile(new File(remapDir, "R_DQJH.dat")).getLines.foreach(line => {
        val items = line.split(" +").toList
        pw.println(s"${items(0)}  ${items(1).substring(0, 4).toInt}   ${items(1).substring(4, 6).toInt}   ${items(1).substring(6, 8).toInt}  ${items(2).substring(0, 2).toInt}  ${items(2).substring(2, 4).toInt}   ${items(2).substring(4, 6).toInt}.${items(2).substring(6, 9)}     ${items(7)}    0   0  ${items(3)}   ${items(5)}     0  0    0")
        pwangle.println(s"${items(3)},${items(5)}")
      })
      pw.close()
      pwangle.close()
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
        println(s"${new File(mfcPath, "ReMapToJHJMFC").getAbsolutePath}")
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

    def calculateWPAs(resultDir: File) {
      val remapPath = new File(resultDir, "ReMap")
      val angleFiles = remapPath.listFiles.filter(file => file.getName.toLowerCase.endsWith(".angle")).toList
      val labPath = new File("tools/wpCalculator")
      val jars = labPath.listFiles.filter(file => file.getName.toLowerCase.endsWith(".jar")).toList
      val classpath = jars.mkString(if (System.getProperty("os.name").toLowerCase.contains("mac")) ":" else ";")
      val anglePath = new File(resultDir, "angles")
      anglePath.mkdirs
      angleFiles.foreach(angleFile => {
        val process = Runtime.getRuntime.exec(s"java -classpath $classpath telescopecalibration.Console $angleFile")
        new Thread(new Runnable {
          override def run: Unit = {
            val expected = 600
            val current = new AtomicInteger(0)
            Source.fromInputStream(process.getInputStream).getLines.foreach(_ => {
              val i = current.incrementAndGet()
              progressUpdate(times.slice(0, 3).sum + (times(3) * math.min(i.toDouble / expected, 1)).toLong)
            })
          }
        }).start
        process.waitFor
        ("csv" :: "png" :: Nil).foreach(ext => new File(angleFile.getAbsolutePath + "." + ext).renameTo(new File(anglePath, angleFile.getName + "." + ext)))
      })
    }
  }

  //run2
  def generateTPTESTTask(basePath: File) = new Task {
    private val times = List(100, 2000, 5000, 20000)
    progressUpdate(0, times.sum)

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
      //      val workbook = new HSSFWorkbook()
      //      val sheet = workbook.createSheet("Polarization Compose")
      //      val headRow = List("方位", "俯仰", "卫星姿态", "JF-A", "JF-E", "姿态补偿", "时间", "时刻", "补偿相位", "额外HWP")
      //      Range(0, tracePlan.items.size + 1).foreach(r => {
      //        val row = sheet.createRow(r)
      //        Range(0, headRow.size).foreach(c => row.createCell(c))
      //      })
      //      headRow.zipWithIndex.foreach(zip => sheet.getRow(0).getCell(zip._2).setCellValue(zip._1))
      //
      //      val attitudeSatelliteFile = DQJHPath.listFiles(new FileFilter {
      //        override def accept(f: File): Boolean = f.getName.contains("LJZ基矢810T") && f.getName.toLowerCase.endsWith(".csv")
      //      }).head
      //      val attitudeSatellite = GeneralRecord.fromCSVFile(attitudeSatelliteFile, "GB2312").asAttitudeSatelliteList
      //      val AESatelliteFiles = DQJHPath.listFiles(new FileFilter {
      //        override def accept(f: File): Boolean = f.getName.contains("双站模式") && f.getName.contains("量子纠缠发射机") && f.getName.contains("引导曲线") && f.getName.toLowerCase.endsWith(".xlsx")
      //      })
      //      val AESatellite = AESatelliteFiles.size match {
      //        case 0 => {
      //          println("No AE Satellite defination.")
      //          new ExperimentRecord(LocalDateTime.now, (0 to tracePlan.items.size).toList.map(i => new RecordItem(LocalDateTime.now, LocalDateTime.now, Some(new TelescopeStatus(0.asDegree, 0.asDegree)))))
      //        }
      //        case _ => {
      //          println("AE Satellite composed.")
      //          GeneralRecord.fromXLSFile(AESatelliteFiles.head).asAESatellite
      //        }
      //      }
      //
      //      val planConfigFile = DQJHPath.listFiles(new FileFilter {
      //        override def accept(f: File): Boolean = f.getName.toLowerCase.endsWith(".xml")
      //      }).head
      //      val planConfig = XML.loadFile(planConfigFile)
      //      val configLJZs = (planConfig \\ "地面站").filter(_.attribute("代号").exists(_.text == "LJZ"))
      //      configLJZs.size match {
      //        case 0 => {
      //          println("No LJZ config found.")
      //          throw new RuntimeException
      //        }
      //        case 1 => {
      //          val configLJZ = configLJZs.head
      //          val startTimeS = (configLJZ \\ "跟踪开始时间").text.replace("B", " ")
      //          val startTime = LocalDateTime.parse(startTimeS, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
      //          val endTimeS = (configLJZ \\ "跟踪结束时间").text.replace("B", " ")
      //          val endTime = LocalDateTime.parse(endTimeS, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
      //          if ((startTime != tracePlan.items.head.time) || (endTime.minusSeconds(1) != tracePlan.items.last.time)) {
      //            println("Time in DQJH not match the config.")
      //            throw new RuntimeException
      //          }
      //        }
      //        case 2 => {
      //          println("Multi LJZ configs found.")
      //          throw new RuntimeException
      //        }
      //      }
      //
      //      (0 until tracePlan.items.size).foreach(i => {
      //        val row = sheet.getRow(i + 1)
      //        row.getCell(0).setCellValue(tracePlan.items(i).telescopeStatus.get.azimuth.degree)
      //        row.getCell(1).setCellValue(tracePlan.items(i).telescopeStatus.get.elevation.degree)
      //        row.getCell(2).setCellValue(attitudeSatellite(i + 1))
      //        row.getCell(3).setCellValue(AESatellite.items(i + 1).telescopeStatus.get.azimuth.degree)
      //        row.getCell(4).setCellValue(AESatellite.items(i + 1).telescopeStatus.get.elevation.degree)
      //        row.getCell(5).setCellType(CellType.FORMULA)
      //        row.getCell(5).setCellFormula(s"C${i + 2}-(E${i + 2}-D${i + 2})")
      //        row.getCell(6).setCellValue(i + 1)
      //        row.getCell(7).setCellValue(tracePlan.items(i).time.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
      //        row.getCell(8).setCellValue(0)
      //        row.getCell(9).setCellValue(0)
      //      })
      //      val fileOutputStream = new FileOutputStream(new File(polComposePath, "偏振补偿.xls"))
      //      workbook.write(fileOutputStream)
      //      fileOutputStream.close


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