package com.github.materiapps.partial

public interface Partial<T> {

    public fun merge(full: T): T

}