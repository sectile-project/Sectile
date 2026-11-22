package dev.sectile.microkernel.scheduler.tick

internal class LaterTasks {
    private var ids = IntArray(16)
    private var runnables = arrayOfNulls<() -> Unit>(16)
    private var delays = LongArray(16)
    private var size = 0

    fun addTask(
        id: Int,
        task: () -> Unit,
        delay: Long,
    ) {
        ensureCapacity()

        ids[size] = id
        runnables[size] = task
        delays[size] = delay
        size++
    }

    fun removeTask(id: Int): Boolean {
        if (id == 0) return false
        for (i in 0 until size) {
            if (ids[i] != id) continue
            val lastIndex = size - 1
            if (i < lastIndex) {
                System.arraycopy(ids, i + 1, ids, i, lastIndex - i)
                System.arraycopy(runnables, i + 1, runnables, i, lastIndex - i)
                System.arraycopy(delays, i + 1, delays, i, lastIndex - i)
            }
            ids[lastIndex] = 0
            runnables[lastIndex] = null
            delays[lastIndex] = 0L
            size--
            return true
        }
        return false
    }

    fun tickAndDrainTo(batch: TaskBatch) {
        if (size == 0) {
            return
        }

        var writeIndex = 0

        for (readIndex in 0 until size) {
            val task = runnables[readIndex] ?: continue
            val delay = delays[readIndex]

            if (delay <= 0L) {
                batch.add(task)
                continue
            }

            ids[writeIndex] = ids[readIndex]
            runnables[writeIndex] = task
            delays[writeIndex] = delay - 1L
            writeIndex++
        }

        ids.fill(0, writeIndex, size)
        runnables.fill(null, writeIndex, size)
        delays.fill(0L, writeIndex, size)

        size = writeIndex
    }

    fun cleanup() {
        if (size == 0) {
            return
        }

        ids.fill(0, 0, size)
        runnables.fill(null, 0, size)
        delays.fill(0L, 0, size)

        size = 0
    }

    private fun ensureCapacity() {
        if (size < runnables.size) {
            return
        }

        val newSize = runnables.size * 2

        ids = ids.copyOf(newSize)
        runnables = runnables.copyOf(newSize)
        delays = delays.copyOf(newSize)
    }
}
