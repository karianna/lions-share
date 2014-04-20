object Something extends App {
  println("hello world")

  (1 to 20) foreach {j =>
    // create a big object to simulate medium-lived active objects on the heap
    val big = (1 to 1000000).toList
    (1 to 100000000) foreach { i =>
//        i
      Some(i)
    }
    // just to keep the ref alive
    big.nonEmpty
  }

  val uptime = java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime()
  println("goodbye world, I took " + uptime)
}
