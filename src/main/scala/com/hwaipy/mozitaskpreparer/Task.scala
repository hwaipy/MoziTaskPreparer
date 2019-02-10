package com.hwaipy.mozitaskpreparer

import java.util.concurrent.atomic.{AtomicLong, AtomicReference}

abstract class Task {
  private val workload = new AtomicLong(0)
  private val finished = new AtomicLong(0)
  private val listener = new AtomicReference[Option[(Long, Long) => Unit]](None)

  def progress = (finished.get, workload.get)

  def progressListener(listener: (Long, Long) => Unit) = this.listener set Some(listener)

  protected def progressUpdate(finished: Long, workload: Long = 0) = {
    if (workload > 0) this.workload set workload
    if (finished < 0 || finished > this.workload.get) throw new IllegalArgumentException("finished should be in [0, workload].")
    this.finished set finished
    listener.get.foreach(l => l(this.finished.get, this.workload.get))
  }

  def run: Unit
}

class SerialTask(val tasks: List[Task]) extends Task {
  private def listener(finished: Long, workload: Long) = {
    val progresses = tasks.map(task => task.progress)
    progressUpdate(progresses.map(_._1).sum, progresses.map(_._2).sum)
  }

  tasks.foreach(_.progressListener(listener))

  override def run = tasks.foreach(_.run)
}

class SimulationTask(duration: Long, updatePeriod: Long = 50) extends Task {
  progressUpdate(0, duration)

  override def run = {
    val startTime = System.nanoTime() / 1000000
    var currentTime = System.nanoTime() / 1000000
    val stopTime = startTime + duration
    while (currentTime < stopTime) {
      Thread.sleep(updatePeriod)
      currentTime = System.nanoTime() / 1000000
      progressUpdate(math.min(currentTime - startTime, duration))
    }
  }
}