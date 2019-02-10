package com.hwaipy.mozitaskpreparer

import java.io.{File, FileFilter, PrintWriter}
import java.nio.file.{Files, StandardCopyOption}
import java.util.concurrent.atomic.AtomicInteger
import collection.JavaConverters._
import scala.io.Source

object TelescopePreparationTasks {
  def generateTrackingTraceTask(basePath: File) = new Task {
    private val times = List(100, 2000, 5000, 20000)
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
      calculateWPAs(resultDir)
      progressUpdate(times.slice(0, 4).sum)
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

  private def clean(file: File) {
    if (file.isDirectory) file.listFiles.foreach(clean)
    if (file.exists) file.delete
  }

}
