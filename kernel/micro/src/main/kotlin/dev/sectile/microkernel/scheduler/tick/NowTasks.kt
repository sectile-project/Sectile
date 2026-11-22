package dev.sectile.microkernel.scheduler.tick

internal class NowTasks {
    private var ids = IntArray(16)
    private var tasks = arrayOfNulls<() -> Unit>(16)
    private var size = 0

    fun addTask(
        task: () -> Unit,
        id: Int = 0,
    ) {
        ensureCapacity()
        ids[size] = id
        tasks[size] = task
        size++
    }

    fun removeTask(id: Int): Boolean {
        if (id == 0) return false
        for (i in 0 until size) {
            if (ids[i] != id) continue
            val lastIndex = size - 1
            if (i < lastIndex) {
                System.arraycopy(ids, i + 1, ids, i, lastIndex - i)
                System.arraycopy(tasks, i + 1, tasks, i, lastIndex - i)
            }
            ids[lastIndex] = 0
            tasks[lastIndex] = null
            size--
            return true
        }
        return false
    }

    fun drainTo(batch: TaskBatch) {
        if (size == 0) {
            return
        }

        for (i in 0 until size) {
            val task = tasks[i] ?: continue
            batch.add(task)
            ids[i] = 0
            tasks[i] = null
        }

        size = 0
    }

    fun cleanup() {
        if (size == 0) {
            return
        }

        ids.fill(0, 0, size)
        tasks.fill(null, 0, size)
        size = 0
    }

    private fun ensureCapacity() {
        if (size < tasks.size) {
            return
        }

        val newSize = tasks.size * 2
        ids = ids.copyOf(newSize)
        tasks = tasks.copyOf(newSize)
    }
}
