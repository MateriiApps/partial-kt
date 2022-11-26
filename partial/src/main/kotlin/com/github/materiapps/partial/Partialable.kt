package com.github.materiapps.partial

/**
 * Base class that all generated partial values extend from.
 * You can use this for referencing a generic partial.
 */
public interface Partialable<T> {
    public fun merge(full: T): T
}
