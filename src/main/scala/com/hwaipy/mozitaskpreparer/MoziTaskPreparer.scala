package com.hwaipy.mozitaskpreparer

import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafx.scene.control._
import scalafx.scene.layout.{HBox, VBox}
import scalafx.util.converter.FormatStringConverter
import java.text.SimpleDateFormat
import java.util.Date
import javafx.event.ActionEvent
import scalafx.geometry.Insets

import collection.JavaConverters._

object MoziTaskPreparer extends JFXApp {
  //  val storageFile = new File("../../Google Drive/ToDo.xml")
  //  //  val storageFile = new File("ToDo.xml")
  //  val actionSet = ActionSet.loadFromFile(storageFile)
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
    val format = new SimpleDateFormat("yyyy-MM-dd")
    val converter = new FormatStringConverter[Number](format)
    val textField = new TextField {
      textFormatter = new TextFormatter(converter)
    }

    def actionToday = textField.text = format.format(new Date(System.currentTimeMillis - 8 * 3600000))

    val buttonToday = new Button("Today") {
      onAction = (ae: ActionEvent) => actionToday
    }

    actionToday
    val childrenSeq = Seq(labelDate, textField, buttonToday)
    childrenSeq.foreach(_.prefHeight <== height)
    children = childrenSeq
    padding = Insets(3, 20, 3, 20)
  }

  lazy val processPane = new VBox() {
    val progressBar = new ProgressBar()
    val buttonProcessPane = new VBox() {
      val buttonProcess = new Button("Process") {
        onAction = (ae: ActionEvent) => println("Process")
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

  def p() = {
    //    val dateS = LocalDateTime.now.minusHours(8).format(DateTimeFormatter.ofPattern("yyyyMMdd"))
    //    val basePath = new File(s"../../实验计划/$dateS")
    //    val fileDQJH = new File(basePath, "短期计划").listFiles(new FileFilter {
    //      override def accept(f: File): Boolean = f.isDirectory && f.getName.endsWith("LJ")
    //    }).head.listFiles(new FileFilter {
    //      override def accept(f: File): Boolean = f.getName.toLowerCase.endsWith(".dat")
    //    }).head
    //    val planPath = new File(basePath, "plan")
    //    if (planPath.exists) {
    //      println("Plan already generated.")
    //    }
    //    planPath.mkdirs
    //    traceDQJH(planPath, fileDQJH)
  }
}