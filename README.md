# Java Threads Demo

Two small Swing applications that demonstrate the bounded-buffer producer/consumer problem with Java threads.
Each window has `Produce-1/2/3` buttons on the left, `Consume-1/2/3` buttons on the right and a label at the
bottom showing the buffer contents. Clicking a button starts a short-lived background thread that puts or takes
that many random integers; a Swing timer refreshes the label about ten times a second, and each operation is
also printed to the console. The buffer holds 5 items, so for example producing 3 twice blocks the second
producer until something is consumed.

The two programs differ only in how the buffer is synchronized:

| Class | File | Synchronization |
|---|---|---|
| `ProducerConsumerMonitor` | `src/main/java/ProducerConsumerMonitor.java` | Monitor style: `synchronized` methods with `wait()` / `notifyAll()` (`BoundedBuffer`). |
| `ProducerConsumerSemaphore` | `src/main/java/ProducerConsumerSemaphore.java` | Two counting `Semaphore`s (`emptySlots`, `fullSlots`) plus a `ReentrantLock` around the queue (`SemaphoreBuffer`). |

Both classes are in the default package.

## Requirements

- JDK 11 or newer (the POM compiles with `--release 11`)
- Maven 3.6 or newer

## Build and run

```bash
mvn compile

# Monitor version (default profile)
mvn exec:java

# Semaphore version
mvn exec:java -P semaphore

# Or run the compiled classes directly
java -cp target/classes ProducerConsumerMonitor
java -cp target/classes ProducerConsumerSemaphore
```

The `exec-maven-plugin` is configured in `pom.xml`; the `monitor` profile (active by default) and the
`semaphore` profile only choose the main class. The programs open a window, so they need a display.

## VS Code

`.vscode/launch.json` has Run/Debug configurations for both classes. They reference a `preLaunchTask` named
`compile`, but no `.vscode/tasks.json` is included, so either run `mvn compile` first or add a task with that
label.
