package com.study.organizer.model;

/**
 * The four states a study session can be in.
 *
 * <p>The lifecycle is a simple state machine:
 *
 * <pre>
 *   IDLE --start--&gt; RUNNING --pause--&gt; PAUSED
 *                     ^                  |
 *                     +-----resume-------+
 *
 *   RUNNING or PAUSED --finish--&gt; FINISHED   (session is saved)
 *   RUNNING or PAUSED --cancel--&gt; IDLE       (session is discarded)
 * </pre>
 *
 * <p>Keeping the states in an enum (rather than, say, a String or an int) means
 * the compiler can check every place we switch on the state, and an invalid
 * state simply cannot be constructed.
 */
public enum SessionState {

    /** No session is active. This is the starting state. */
    IDLE,

    /** A session is active and the clock is ticking. */
    RUNNING,

    /** A session is active but the clock is stopped. Paused time is not counted. */
    PAUSED,

    /** The session ended and was saved with a summary. */
    FINISHED
}
