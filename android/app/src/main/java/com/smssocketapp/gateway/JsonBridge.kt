package com.smssocketapp.gateway

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import org.json.JSONArray
import org.json.JSONObject

object JsonBridge {
  fun toWritableMap(jsonObject: JSONObject): WritableMap {
    val map = Arguments.createMap()
    val iterator = jsonObject.keys()

    while (iterator.hasNext()) {
      val key = iterator.next()
      when (val value = jsonObject.opt(key)) {
        null, JSONObject.NULL -> map.putNull(key)
        is Boolean -> map.putBoolean(key, value)
        is Int -> map.putInt(key, value)
        is Long -> map.putDouble(key, value.toDouble())
        is Double -> map.putDouble(key, value)
        is Float -> map.putDouble(key, value.toDouble())
        is String -> map.putString(key, value)
        is JSONObject -> map.putMap(key, toWritableMap(value))
        is JSONArray -> map.putArray(key, toWritableArray(value))
        else -> map.putString(key, value.toString())
      }
    }

    return map
  }

  fun toWritableArray(jsonArray: JSONArray): WritableArray {
    val array = Arguments.createArray()

    for (index in 0 until jsonArray.length()) {
      when (val value = jsonArray.opt(index)) {
        null, JSONObject.NULL -> array.pushNull()
        is Boolean -> array.pushBoolean(value)
        is Int -> array.pushInt(value)
        is Long -> array.pushDouble(value.toDouble())
        is Double -> array.pushDouble(value)
        is Float -> array.pushDouble(value.toDouble())
        is String -> array.pushString(value)
        is JSONObject -> array.pushMap(toWritableMap(value))
        is JSONArray -> array.pushArray(toWritableArray(value))
        else -> array.pushString(value.toString())
      }
    }

    return array
  }

  fun readableMapToJson(readableMap: ReadableMap): JSONObject {
    val jsonObject = JSONObject()
    val iterator = readableMap.keySetIterator()

    while (iterator.hasNextKey()) {
      val key = iterator.nextKey()
      when (readableMap.getType(key)) {
        ReadableType.Null -> jsonObject.put(key, JSONObject.NULL)
        ReadableType.Boolean -> jsonObject.put(key, readableMap.getBoolean(key))
        ReadableType.Number -> jsonObject.put(key, readableMap.getDouble(key))
        ReadableType.String -> jsonObject.put(key, readableMap.getString(key))
        ReadableType.Map -> jsonObject.put(key, readableMapToJson(readableMap.getMap(key)!!))
        ReadableType.Array -> jsonObject.put(key, readableArrayToJson(readableMap.getArray(key)!!))
      }
    }

    return jsonObject
  }

  private fun readableArrayToJson(readableArray: ReadableArray): JSONArray {
    val jsonArray = JSONArray()

    for (index in 0 until readableArray.size()) {
      when (readableArray.getType(index)) {
        ReadableType.Null -> jsonArray.put(JSONObject.NULL)
        ReadableType.Boolean -> jsonArray.put(readableArray.getBoolean(index))
        ReadableType.Number -> jsonArray.put(readableArray.getDouble(index))
        ReadableType.String -> jsonArray.put(readableArray.getString(index))
        ReadableType.Map -> jsonArray.put(readableMapToJson(readableArray.getMap(index)!!))
        ReadableType.Array -> jsonArray.put(readableArrayToJson(readableArray.getArray(index)!!))
      }
    }

    return jsonArray
  }
}
