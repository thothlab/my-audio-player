package tech.thothlab.dombra.presentation.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class SchedulerProbeTest {

    private class Probe(scope: CoroutineScope) {
        val flow = MutableStateFlow(0)
        var collected = -1
        var jobRan = false
        init {
            scope.launch { flow.collect { collected = it } }
            scope.launch { jobRan = true }
        }
    }

    @Test
    fun backgroundScopeRunsInitJobs() = runTest {
        val probe = Probe(backgroundScope)
        kotlinx.coroutines.yield()
        advanceUntilIdle()
        assertEquals(true, probe.jobRan, "launched job must run")
        probe.flow.value = 42
        kotlinx.coroutines.yield()
        advanceUntilIdle()
        assertEquals(42, probe.collected, "state flow collector must observe")
    }
}
