import sbt._
import Keys._

object LionPlugin extends Plugin {
  override lazy val settings = Seq(commands += lion)

  lazy val lion = Command.command("runLion") { (state: State) =>
    println("Hello World!")
    state
  }
}
