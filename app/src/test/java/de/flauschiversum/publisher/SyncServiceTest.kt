package de.flauschiversum.publisher

import de.flauschiversum.publisher.git.GitFailure
import de.flauschiversum.publisher.git.SyncReason
import de.flauschiversum.publisher.git.SyncService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncServiceTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var now = 1_000_000L

    private fun waitUntil(cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 5000
        while (!cond() && System.currentTimeMillis() < end) Thread.sleep(5)
        assertTrue("Zeitüberschreitung im Test", cond())
    }

    @Test fun paralleleAusloeserStartenNurEinenLauf() {
        val runs = AtomicInteger(); val gate = CountDownLatch(1)
        val s = SyncService(scope, { now }, 60_000, classify = { GitFailure.Other("x") }, action = { runs.incrementAndGet(); gate.await(5, TimeUnit.SECONDS) })
        assertTrue(s.request(SyncReason.START))
        assertFalse(s.request(SyncReason.OPEN_POST)); assertFalse(s.request(SyncReason.FOREGROUND)); assertFalse(s.request(SyncReason.MANUAL))
        assertTrue(s.state.value.running)
        gate.countDown(); waitUntil { !s.state.value.running }
        assertEquals(1, runs.get())
    }

    @Test fun mindestabstandGiltFuerAutomatischeLaeufeNichtFuerManuelle() {
        val runs = AtomicInteger()
        val s = SyncService(scope, { now }, 60_000, classify = { GitFailure.Other("x") }, action = { runs.incrementAndGet() })
        assertTrue(s.request(SyncReason.START)); waitUntil { !s.state.value.running }
        now += 10_000
        assertFalse("zu früh", s.request(SyncReason.FOREGROUND))
        assertTrue("manuell geht immer", s.request(SyncReason.MANUAL)); waitUntil { !s.state.value.running && runs.get() == 2 }
        now += 61_000
        assertTrue(s.request(SyncReason.OPEN_POST)); waitUntil { runs.get() == 3 }
    }

    @Test fun fehlerSindStillUndWerdenGemerkt() {
        val s = SyncService(scope, { now }, 0, classify = { GitFailure.NoNetwork }, action = { error("kein Netz") })
        assertTrue(s.request(SyncReason.START)); waitUntil { !s.state.value.running }
        assertEquals(GitFailure.NoNetwork, s.state.value.lastFailure)
        assertEquals(0L, s.state.value.lastSuccessMillis)
        assertFalse(s.state.value.accessProblem)
    }

    @Test fun erfolgSetztZeitpunktUndLoeschtFehler() {
        var fail = true
        val s = SyncService(scope, { now }, 0, classify = { GitFailure.NoNetwork }, action = { if (fail) error("x") })
        s.request(SyncReason.START); waitUntil { !s.state.value.running }
        fail = false; now += 5
        s.request(SyncReason.MANUAL); waitUntil { !s.state.value.running && s.state.value.lastFailure == null }
        assertEquals(now, s.state.value.lastSuccessMillis)
    }

    @Test fun zweimalZugriffVerweigertZeigtHinweisNetzfehlerNicht() {
        var failure: GitFailure = GitFailure.KeyRejected
        val s = SyncService(scope, { now }, 0, classify = { failure }, action = { error("x") })
        s.request(SyncReason.START); waitUntil { !s.state.value.running }
        assertFalse("erst einmal", s.state.value.accessProblem)
        failure = GitFailure.NoNetwork
        s.request(SyncReason.MANUAL); waitUntil { !s.state.value.running && s.state.value.generation == 2L }
        assertFalse("Netzfehler zählt nicht", s.state.value.accessProblem)
        failure = GitFailure.KeyRejected
        s.request(SyncReason.MANUAL); waitUntil { !s.state.value.running && s.state.value.generation == 3L }
        assertTrue(s.state.value.accessProblem)
    }

    @Test fun anfordernBlockiertNicht() = runBlocking {
        val gate = CountDownLatch(1)
        val s = SyncService(scope, { now }, 0, classify = { GitFailure.Other("x") }, action = { gate.await(5, TimeUnit.SECONDS) })
        val t0 = System.nanoTime()
        s.request(SyncReason.START)
        assertTrue("request() muss sofort zurückkehren", (System.nanoTime() - t0) < 200_000_000L)
        gate.countDown(); waitUntil { !s.state.value.running }
    }
}
