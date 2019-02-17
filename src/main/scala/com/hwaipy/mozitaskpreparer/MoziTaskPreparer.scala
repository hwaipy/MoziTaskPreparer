package com.hwaipy.mozitaskpreparer

import java.awt.Desktop
import java.io.{File, FileInputStream, PrintWriter}

import scalafx.application.{JFXApp, Platform}
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafx.scene.control._
import scalafx.scene.layout.{AnchorPane, BorderPane, HBox, VBox}
import scalafx.util.converter.FormatStringConverter
import java.text.{NumberFormat, SimpleDateFormat}
import java.util.{Date, Properties}
import java.util.concurrent.Executors
import javafx.event.ActionEvent

import collection.JavaConverters._
import scalafx.beans.property.{BooleanProperty, DoubleProperty, StringProperty}
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
  val modeLZXSelected = BooleanProperty(true)
  val phaseText = StringProperty("0")

  stage = new PrimaryStage {
    title = "Mozi Task Preparer"
    scene = new Scene() {
      val rootPane = new VBox(spacing = 5) {
        children = Seq(datePane, parameterPane, processPane)
      }
      rootPane.getStylesheets.add(ClassLoader.getSystemClassLoader.getResource("com/hwaipy/mozitaskpreparer/gui/LAF.css").toExternalForm)
      root = rootPane
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

  lazy val parameterPane = new VBox() {
    children = Seq(new VBox() {
      val modeAndPhaseHBox = new HBox() {
        val modeHBox = new HBox(5.0) {
          padding = Insets(7, 10, 7, 10)
          val tog = new ToggleGroup()
          val modeLZX = new RadioButton() {
            text = "量子星"
            toggleGroup = tog
            selected <==> modeLZXSelected
          }
          val mode921 = new RadioButton() {
            text = "921"
            toggleGroup = tog
          }
          modeLZX.prefHeight <== height
          mode921.prefHeight <== height
          children = Seq(modeLZX, mode921)
        }
        val phaseHBox = new HBox() {
          padding = Insets(7, 10, 7, 10)
          val label = new Label("Phase: ")
          val textField = new TextField() {
            val format = NumberFormat.getInstance()
            val converter = new FormatStringConverter[Number](format)
            textFormatter = new TextFormatter(converter)
            text <==> phaseText
            prefWidth = 50
          }
          label.prefHeight <== height
          textField.prefHeight <== height
          children = Seq(label, textField)
        }
        children = Seq(modeHBox, phaseHBox)
      }
      children = Seq(modeAndPhaseHBox)
      id = "ParameterPane"
      padding = Insets(1, 3, 1, 3)
    })
    padding = Insets(1, 10, 1, 10)
  }

  def taskPrepare = {
    properties.put("Phase Satellite", phaseText.value)
    properties.put("Mode", modeLZXSelected.value.toString)
    val root = s"${properties.getProperty("DataRoot", "data")}/${dateTextField.text.getValue.replaceAll("-", "")}"
    val tasks = TelescopePreparationTasks.generatePreparationTask(new File(root), properties.keys().asScala.toList.map(_.toString).map(key => (key, properties.getProperty(key))).toMap)
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