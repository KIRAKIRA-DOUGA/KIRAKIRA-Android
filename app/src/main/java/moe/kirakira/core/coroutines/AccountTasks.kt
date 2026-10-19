package moe.kirakira.core.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

/** A replaceable batch of account work, cancelled with its owning ViewModel. */
internal class AccountTasks(owner: CoroutineScope) {
    private val job = SupervisorJob(owner.coroutineContext[Job])
    private val scope = CoroutineScope(owner.coroutineContext + job)

    fun cancelCurrent() = job.cancelChildren()

    fun launch(block: suspend () -> Unit): Job = scope.launch { block() }
}
