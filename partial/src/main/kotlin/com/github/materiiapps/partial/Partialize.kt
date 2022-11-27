package com.github.materiiapps.partial

/**
 * Generates a Partial subclass with all constructor parameters
 * boxed in [Partial] and marked [Partial.Missing] by default.
 */
@Target(AnnotationTarget.CLASS)
public annotation class Partialize(
    /**
     * Marks this class as the parent (extendable) partial.
     */
    val parent: Boolean = false
)
