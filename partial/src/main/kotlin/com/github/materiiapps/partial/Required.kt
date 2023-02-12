package com.github.materiiapps.partial

/**
 * Marks a field as required when partializing,
 * (Does not box the field in a [Partial] during deserialization)
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
public annotation class Required
