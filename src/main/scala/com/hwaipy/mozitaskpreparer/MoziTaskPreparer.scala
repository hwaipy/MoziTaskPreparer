package com.hwaipy.mozitaskpreparer

import java.awt.Desktop
import java.io.{File, FileInputStream, PrintWriter}

import scalafx.application.{JFXApp, Platform}
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafx.scene.control._
import scalafx.scene.layout.{HBox, VBox}
import scalafx.util.converter.FormatStringConverter
import java.text.SimpleDateFormat
import java.util.{Date, Properties}
import java.util.concurrent.Executors

import javafx.event.ActionEvent
import scalafx.beans.property.DoubleProperty
import scalafx.geometry.Insets

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import java.nio.file.{Files, Paths}

object MoziTaskPreparer extends JFXApp {
  implicit val executionContext = ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor((r: Runnable) => {
    val thread = new Thread(r)
    thread.setDaemon(true)
    thread
  }))
  val properties = new Properties()
  val configIn = new FileInputStream("config.xml")
  properties.loadFromXML(configIn)
  configIn.close()

  val workProgress = new DoubleProperty()
  workProgress.value = 0.0
  val format = new SimpleDateFormat("yyyy-MM-dd")
  val converter = new FormatStringConverter[Number](format)
  val dateTextField = new TextField {
    textFormatter = new TextFormatter(converter)
  }

  stage = new PrimaryStage {
    title = "Mozi Task Preparer"
    scene = new Scene() {
      root = new VBox(spacing = 5) {
        children = Seq(datePane, processPane)
      }
    }
  }

  lazy val datePane = new HBox(spacing = 5) {
    val labelDate = new Label("Date (UTC): ")

    def actionToday = dateTextField.text = format.format(new Date(System.currentTimeMillis - 8 * 3600000))

    val buttonToday = new Button("Today") {
      onAction = (ae: ActionEvent) => actionToday
    }
    actionToday
    dateTextField.text = "2017-02-16"
    val childrenSeq = Seq(labelDate, dateTextField, buttonToday)
    childrenSeq.foreach(_.prefHeight <== height)
    children = childrenSeq
    padding = Insets(3, 20, 3, 20)
  }

  lazy val processPane = new VBox() {
    val progressBar = new ProgressBar()
    progressBar.progress <== workProgress
    val buttonProcessPane = new VBox() {
      val buttonProcess = new Button("Process") {
        onAction = (ae: ActionEvent) => {
          disable = true
          val future = onButtonProcess
          future onComplete {
            case Success(s) => {
              disable = false
            }
            case Failure(f) => {
              handleException(f)
              disable = false
            }
          }
        }
      }
      buttonProcess.prefWidth <== width
      children = Seq(buttonProcess)
      this.padding = Insets(3, 80, 3, 80)
    }
    val childrenSeq = Seq(progressBar, buttonProcessPane)
    childrenSeq.foreach(_.prefWidth <== width)
    children = childrenSeq
    padding = Insets(3, 20, 3, 20)
  }

  def onButtonProcess = Future {
    taskPrepare
  }

  def taskPrepare = {
    val root = s"${properties.getProperty("DataRoot", "data")}/${dateTextField.text.getValue.replaceAll("-", "")}"
    val trackingTraceTask = TelescopePreparationTasks.generateTrackingTraceTask(new File(root))
    val polarizationControlTask = TelescopePreparationTasks.generatePolarizationControlTask(new File(root))
    val waveplateCalculateTask = TelescopePreparationTasks.generateWaveplateCalculateTask(new File(root))
    val tasks = new SerialTask(List(trackingTraceTask, polarizationControlTask, waveplateCalculateTask))
    tasks.progressListener((finished, workload) => Platform.runLater(() => workProgress.value = finished.toDouble / workload))
    tasks.run
  }

  def handleException(e: Throwable) = {
    val logPath = properties.getProperty("LogPath", "log")
    Files.createDirectories(Paths.get(logPath))
    val logName = s"Error[${new SimpleDateFormat("yyyy-MM-dd HH-mm-SS.sss").format(new Date())}].log"
    val path = s"${logPath}/${logName}"
    val pw = new PrintWriter(path)
    pw.println(e.getMessage)
    pw.println("--------------------------------------------------------------------------------")
    e.printStackTrace(pw)
    pw.close()
    Desktop.getDesktop.open(new File(path))
  }
}