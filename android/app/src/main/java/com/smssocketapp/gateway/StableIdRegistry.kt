package com.smssocketapp.gateway

import java.util.IdentityHashMap
import java.util.UUID

class StableIdRegistry<T : Any> {
  private val ids = IdentityHashMap<T, String>()

  @Synchronized
  fun idFor(value: T): String = ids.getOrPut(value) { UUID.randomUUID().toString() }

  @Synchronized
  fun remove(value: T) {
    ids.remove(value)
  }
}
