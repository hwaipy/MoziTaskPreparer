package com.hwaipy.mozitaskpreparer

import java.nio.file._
import org.scalatest._

class MoziTaskPreparerTest extends FunSuite with BeforeAndAfter with BeforeAndAfterAll {
  private val testPath = Paths.get("target/testregion/watchserviceutil")

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

  test("Test the usage of WatchService API.") {
    assert(true)
  }
}