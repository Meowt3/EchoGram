package com.echogram.app.data.telegram

import android.net.Uri
import com.echogram.app.data.model.ParsedProxy
import com.echogram.app.data.model.ProxyType

object ProxyLinkParser {
    fun parse(rawInput: String): ParsedProxy? {
        val text = rawInput.trim()
        if (text.isBlank()) return null
        val uri = runCatching { Uri.parse(text) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path?.lowercase().orEmpty()

        val isMtproto = (scheme == "tg" && host == "proxy") ||
            ((scheme == "https" || scheme == "http") && (host == "t.me" || host == "telegram.me") && path.startsWith("/proxy"))
        val isSocks = (scheme == "tg" && host == "socks") ||
            ((scheme == "https" || scheme == "http") && (host == "t.me" || host == "telegram.me") && path.startsWith("/socks"))

        if (!isMtproto && !isSocks) return null

        val server = uri.getQueryParameter("server")?.trim().orEmpty()
        val port = uri.getQueryParameter("port")?.trim()?.toIntOrNull() ?: return null
        if (server.isBlank() || port !in 1..65535) return null

        return if (isMtproto) {
            val secret = uri.getQueryParameter("secret")?.trim().orEmpty()
            if (secret.isBlank()) return null
            ParsedProxy(
                type = ProxyType.MTPROTO,
                server = server,
                port = port,
                secret = secret
            )
        } else {
            ParsedProxy(
                type = ProxyType.SOCKS5,
                server = server,
                port = port,
                username = uri.getQueryParameter("user")?.trim().orEmpty(),
                password = uri.getQueryParameter("pass")?.trim().orEmpty()
            )
        }
    }
}
