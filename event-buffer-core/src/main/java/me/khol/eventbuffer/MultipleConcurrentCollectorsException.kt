package me.khol.eventbuffer

/**
 * Delivering an event to multiple collectors is problematic and thus not supported.
 *
 * See the "note on multicasting" section in [EventBuffer]'s description.
 */
class MultipleConcurrentCollectorsException :
    IllegalStateException("Multiple concurrent collectors are not supported.")
