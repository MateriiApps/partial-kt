package com.github.materiiapps.partial

/**
 * Omits a field from being included when partializing a class.
 *
 * **NOTE**: This makes a default value required for the property,
 * otherwise a merge wouldn't be possible.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
public annotation class Skip
