/*
 * Javalin - https://javalin.io
 * Copyright 2017 David Åse
 * Licensed under Apache 2.0: https://github.com/tipsy/javalin/blob/master/LICENSE
 */

package io.javalin.core.util

import io.javalin.BasicAuthCredentials
import io.javalin.Context
import io.javalin.core.HandlerEntry
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.lang.Exception
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object ContextUtil {

    fun create(response: HttpServletResponse, request: HttpServletRequest): Context {
        return Context(response, request, HashMap<String, String>(), ArrayList<String>())
    }

    fun update(ctx: Context, handlerEntry: HandlerEntry, requestUri: String): Context {
        val requestList = Util.pathToList(requestUri)
        val matchedList = Util.pathToList(handlerEntry.path)
        ctx.paramMap = getParams(requestList, matchedList)
        ctx.splatList = getSplat(requestList, matchedList)
        return ctx
    }

    fun getSplat(request: List<String>, matched: List<String>): List<String> {
        val numRequestParts = request.size
        val numHandlerParts = matched.size
        val splat = ArrayList<String>()
        var i = 0
        while (i < numRequestParts && i < numHandlerParts) {
            val matchedPart = matched[i]
            if (matchedPart == "*") {
                val splatParam = StringBuilder(request[i])
                if (numRequestParts != numHandlerParts && i == numHandlerParts - 1) {
                    for (j in i + 1..numRequestParts - 1) {
                        splatParam.append("/")
                        splatParam.append(request[j])
                    }
                }
                splat.add(urlDecode(splatParam.toString()))
            }
            i++
        }
        return splat
    }

    fun getParams(requestPaths: List<String>, handlerPaths: List<String>): Map<String, String> {
        val params = HashMap<String, String>()
        var i = 0
        while (i < requestPaths.size && i < handlerPaths.size) {
            val matchedPart = handlerPaths[i]
            if (matchedPart.startsWith(":")) {
                params[matchedPart.toLowerCase()] = urlDecode(requestPaths[i])
            }
            i++
        }
        return params
    }

    fun urlDecode(s: String): String = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8").replace("%2B", "+")

    fun mapKeysOrReturnNullIfAnyNulls(keys: Array<out String>, f: (s: String) -> String?): List<String>? = try {
        keys.map { f.invoke(it) }.requireNoNulls().toList()
    } catch (e: IllegalArgumentException) {
        null
    }

    fun byteArrayToString(bytes: ByteArray, encoding: String?): String {
        var string: String
        if (encoding != null && Charset.isSupported(encoding)) {
            try {
                string = String(bytes, Charset.forName(encoding))
            } catch (e: UnsupportedEncodingException) {
                string = String(bytes)
            }
        } else {
            string = String(bytes)
        }
        return string
    }

    fun toByteArray(input: InputStream): ByteArray {
        val baos = ByteArrayOutputStream()
        val byteBuffer = ByteArray(1024)
        var b = input.read(byteBuffer)
        while (b != -1) {
            baos.write(byteBuffer, 0, b)
            b = input.read(byteBuffer)
        }
        return baos.toByteArray()
    }

    fun setCorsOptions(ctx: Context) {
        ctx.header("Access-Control-Request-Headers")?.let {
            ctx.header("Access-Control-Allow-Headers", it)
        }
        ctx.header("Access-Control-Request-Method")?.let {
            ctx.header("Access-Control-Allow-Methods", it)
        }
    }

    fun enableCors(ctx: Context, origins: Array<String>) {
        val header = ctx.header("Origin") ?: ctx.header("Referer") ?: return
        origins.map { it.removeSuffix("/") }.firstOrNull { header.startsWith(it) }?.let {
            ctx.header("Access-Control-Allow-Origin", it)
        }
    }

    fun getBasicAuthCredentials(header: String?): BasicAuthCredentials? = try {
        val (username, password) = String(Base64.getDecoder().decode(header!!.removePrefix("Basic "))).split(":")
        BasicAuthCredentials(username, password)
    } catch (e: Exception) {
        null
    }

}
