package com.coordinadora.camerascannerlibrary.barcodescanner

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

class ScopedExecutor(private val executor: Executor) : Executor {
    private val shutdown = AtomicBoolean()

    override fun execute(command: Runnable) {
        if (shutdown.get()) {
            return
        }

        executor.execute {
            if (shutdown.get()) {
                return@execute
            }
            command.run()
        }
    }

    fun shutdown() {
        shutdown.set(true)
    }
}