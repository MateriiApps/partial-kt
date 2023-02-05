package com.github.materiiapps.partial

import kotlin.reflect.KClass

/**
 * Generates a Partial subclass with all constructor parameters
 * boxed in [Partial] and marked [Partial.Missing] by default.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class Partialize(
    /**
     * If an interface is marked with [Partialize],
     * a generic merge method is generated for it that merges same-type [children] partials or null.
     *
     * THIS DOES NOT CHECK FOR VALIDITY, make sure that your relationship is valid!!!!
     * (No complex hierarchy, children implement the parent, etc...)
     * A hierarchy more complex than (parent -> children) is NOT supported.
     */
    val children: Array<KClass<*>> = [],
)
