package kui.demo.log

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.stereotype.Component

@SpringBootApplication
class LogDemoApplication

fun main(args: Array<String>) {
    runApplication<LogDemoApplication>(*args)
}

@Component
class LogDemoCommandLineRunner: CommandLineRunner {
    override fun run(vararg args: String?) {
        val log = LoggerFactory.getLogger(LogDemoCommandLineRunner::class.java)
        var counter = 0;
        while (true) {
            log.info("This is info #$counter")
            log.warn("This is warn #$counter")
            log.error("This is error #$counter")
            println("This is stdout #$counter")
            System.err.println("This is stderr #$counter")
            counter++
            Thread.sleep(1000)
        }
    }
}
