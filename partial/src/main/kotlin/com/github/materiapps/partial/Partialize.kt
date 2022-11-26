package com.github.materiapps.partial

/**
 * Generates a Partial subclass with all constructor parameters
 * boxed in [PartialValue] and marked [PartialValue.Missing] by default.
 */
@Target(AnnotationTarget.CLASS)
public annotation class Partialize(
    /**
     * Marks this class as the parent (extendable) partial.
     */
    val parent: Boolean = false
)
