package com.nexusstream.app.data

import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.Base64

/**
 * Tiny protobuf writer for the opaque `params` strings InnerTube expects (bell level, search
 * filters, comment actions). Only the wire types those messages use: varint and length-delimited.
 */
internal class Proto {
    private val out = ByteArrayOutputStream()

    fun int(field: Int, value: Long): Proto = apply {
        varint((field shl 3).toLong())
        varint(value)
    }

    fun int(field: Int, value: Int): Proto = int(field, value.toLong())

    fun bool(field: Int, value: Boolean): Proto = int(field, if (value) 1L else 0L)

    fun string(field: Int, value: String): Proto = bytes(field, value.toByteArray())

    fun message(field: Int, build: Proto.() -> Unit): Proto = bytes(field, Proto().apply(build).toByteArray())

    fun bytes(field: Int, value: ByteArray): Proto = apply {
        varint(((field shl 3) or 2).toLong())
        varint(value.size.toLong())
        out.write(value)
    }

    fun toByteArray(): ByteArray = out.toByteArray()

    /** Standard base64, as the website sends it in request bodies. */
    fun base64(): String = Base64.getEncoder().encodeToString(toByteArray())

    /** Base64 then percent-encoded, like YouTube.js's `encodeURIComponent(base64)`. */
    fun base64Url(): String = URLEncoder.encode(base64(), "UTF-8")

    private fun varint(value: Long) {
        var v = value
        while (true) {
            if (v and 0x7FL.inv() == 0L) {
                out.write(v.toInt())
                return
            }
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
    }
}
