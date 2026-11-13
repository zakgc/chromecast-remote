package uk.co.home.chromecastremote.protocol

/** A command belongs to one connection generation and expires rather than being replayed. */
class CommandGate {
    @Volatile var generation: Long = 0; private set
    @Synchronized fun invalidate(): Long { generation++; return generation }
    fun accepts(token: Long, createdAt: Long, now: Long) = token == generation && now - createdAt in 0..250
}
