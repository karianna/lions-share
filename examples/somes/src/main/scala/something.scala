object Something extends App {
  println("hello world")

  val strings = Array("foo", "bar", "baz", "quux")

  def get(i: Int): String = {
    if (i < 1000000000) strings(i&3)
    else throw new Exception("Too big")
  }

  var sum: Long = 0

  (1 to 20) foreach {j =>
    // create a big object to simulate medium-lived active objects on the heap
    val big = (1 to 1000000).toList
    (1 to 100000000) foreach { i =>
      sum += get(i).length
    }
    // just to keep the ref alive
    require(big.nonEmpty)
  }

  val uptime = java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime()
  println("goodbye world, I took " + uptime)
}
