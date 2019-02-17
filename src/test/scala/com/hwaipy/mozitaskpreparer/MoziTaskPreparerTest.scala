package com.hwaipy.mozitaskpreparer

import java.io.{BufferedInputStream, BufferedReader, File, FileInputStream}
import java.nio.file._

import org.scalatest._

import scala.io.Source

class MoziTaskPreparerTest extends FunSuite with BeforeAndAfter with BeforeAndAfterAll {
  private val targetRoot = new File("C:\\Users\\Administrator\\Desktop\\示例数据")
  private val compareRoot = new File("C:\\Users\\Administrator\\Desktop\\示例数据 - 比对")

  override def beforeAll() {
  }

  override def afterAll() {
  }

  before {
  }

  after {
  }

  private def assertWatchEvent(watchEvent: WatchEvent[_], kind: WatchEvent.Kind[_], count: Int, context: Path) {
    assert(watchEvent.kind == kind)
    assert(watchEvent.count == count)
    assert(watchEvent.context == context)
  }

  test("Test TrackPreparation.") {

    val tasks = TelescopePreparationTasks.generatePreparationTask(new File(targetRoot, "20180426"))
    tasks.run
    compare("20180426")
  }

  private def compare(name: String) = {
    val newJHJ = new File(s"${targetRoot}/$name/plan/DQJH/JHJ/").listFiles.toList.filter(f => f.getName.toLowerCase.endsWith("jhj")).head
    val refJHJ = new File(s"${compareRoot}/$name/plan/DQJH/JHJ/").listFiles.toList.filter(f => f.getName.toLowerCase.endsWith("jhj")).head
    compareFile(newJHJ, refJHJ)
    val newJHD = new File(s"${targetRoot}/$name/plan/DQJH/JHJ/").listFiles.toList.filter(f => f.getName.toLowerCase.endsWith("jhd")).head
    val refJHD = new File(s"${compareRoot}/$name/plan/DQJH/JHJ/").listFiles.toList.filter(f => f.getName.toLowerCase.endsWith("jhd")).head
    compareFile(newJHD, refJHD)
    val newAngles = new File(s"${targetRoot}/$name/偏振补偿/").listFiles.toList.filter(f => f.getName.toLowerCase.endsWith("csv")).head
    val refAngles = new File(s"${targetRoot}/$name/偏振补偿/").listFiles.toList.filter(f => f.getName.toLowerCase.endsWith("csv")).head
    compareAngles(newAngles, refAngles)
  }

  private def compareFile(file1: File, file2: File) = {
    val lines1 = Source.fromFile(file1).getLines().toList
    val lines2 = Source.fromFile(file2).getLines().toList
    assert(lines1.size == lines2.size)
    assert(lines1.zip(lines2).forall(z => z._1 == z._2))
  }

  private def compareAngles(file1: File, file2: File) = {
    val lines1 = Source.fromFile(file1).getLines().toList
    val lines2 = Source.fromFile(file2).getLines().toList
    assert(lines1.size == lines2.size)
    assert(lines1.zip(lines2).forall(z => z._1 == z._2))
  }
}