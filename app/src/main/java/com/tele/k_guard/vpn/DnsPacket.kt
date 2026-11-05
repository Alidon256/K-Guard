package com.tele.k_guard.vpn

/** Minimal IPv4/UDP/DNS helpers. Pure Kotlin so they can be unit tested on the JVM. */
object DnsPacket {

    /** A DNS query found inside a raw IPv4 packet read from the TUN interface. */
    class Query(
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val dns: ByteArray
    ) {
        val name: String? get() = parseName(dns)
        val qtype: Int? get() = parseQType(dns)
    }

    /** Returns the DNS query in an IPv4 UDP packet addressed to port 53, or null for anything else. */
    fun parseQuery(packet: ByteArray, length: Int): Query? {
        if (length < 28) return null
        if ((packet[0].toInt() shr 4) != 4) return null
        val ihl = (packet[0].toInt() and 0x0f) * 4
        if (ihl < 20 || length < ihl + 8) return null
        if ((packet[9].toInt() and 0xff) != 17) return null // UDP only
        val total = u16(packet, 2).coerceAtMost(length)
        val srcPort = u16(packet, ihl)
        val dstPort = u16(packet, ihl + 2)
        if (dstPort != 53) return null
        val payloadStart = ihl + 8
        if (total <= payloadStart + 12) return null
        return Query(
            srcIp = packet.copyOfRange(12, 16),
            dstIp = packet.copyOfRange(16, 20),
            srcPort = srcPort,
            dstPort = dstPort,
            dns = packet.copyOfRange(payloadStart, total)
        )
    }

    /** Reads the first question name from a DNS message, lower-cased. Null if malformed or compressed. */
    fun parseName(dns: ByteArray): String? {
        if (dns.size < 13) return null
        // QR bit must be 0 (a query) and there must be at least one question.
        if ((dns[2].toInt() and 0x80) != 0) return null
        if (u16(dns, 4) < 1) return null
        var pos = 12
        val sb = StringBuilder()
        while (true) {
            if (pos >= dns.size) return null
            val len = dns[pos].toInt() and 0xff
            if (len == 0) break
            if (len and 0xC0 != 0 || len > 63) return null // compression pointers are not valid in a question
            if (pos + 1 + len > dns.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 1..len) sb.append((dns[pos + i].toInt() and 0xff).toChar())
            pos += len + 1
        }
        return sb.toString().lowercase()
    }

    /** Record type of the first question (1 = A, 28 = AAAA, ...), or null if the message is malformed. */
    fun parseQType(dns: ByteArray): Int? {
        if (dns.size < 13) return null
        var pos = 12
        while (true) {
            if (pos >= dns.size) return null
            val len = dns[pos].toInt() and 0xff
            if (len == 0) break
            if (len and 0xC0 != 0 || len > 63) return null
            pos += len + 1
        }
        if (pos + 5 > dns.size) return null // null label + QTYPE + QCLASS
        return u16(dns, pos + 1)
    }

    fun typeName(type: Int): String = when (type) {
        1 -> "A"; 2 -> "NS"; 5 -> "CNAME"; 6 -> "SOA"; 12 -> "PTR"; 15 -> "MX"; 16 -> "TXT"
        28 -> "AAAA"; 33 -> "SRV"; 64 -> "SVCB"; 65 -> "HTTPS"
        else -> "TYPE$type"
    }

    /** Builds an NXDOMAIN response for the given query (same id and question, no answers). */
    fun nxDomain(query: ByteArray): ByteArray {
        // Keep header + question only.
        var pos = 12
        while (pos < query.size && query[pos].toInt() != 0) pos += (query[pos].toInt() and 0xff) + 1
        val end = (pos + 1 + 4).coerceAtMost(query.size) // null label + QTYPE + QCLASS
        val out = query.copyOfRange(0, end)
        val rd = out[2].toInt() and 0x01
        out[2] = (0x80 or rd).toByte()          // QR=1, opcode 0, RD copied
        out[3] = 0x83.toByte()                  // RA=1, RCODE=3 (NXDOMAIN)
        out[6] = 0; out[7] = 0                  // ANCOUNT
        out[8] = 0; out[9] = 0                  // NSCOUNT
        out[10] = 0; out[11] = 0                // ARCOUNT
        return out
    }

    /** Wraps a UDP payload in an IPv4 packet (UDP checksum 0, which IPv4 permits). */
    fun wrapUdp(srcIp: ByteArray, dstIp: ByteArray, srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
        val total = 20 + 8 + payload.size
        val p = ByteArray(total)
        p[0] = 0x45.toByte()
        putU16(p, 2, total)
        p[8] = 64                // TTL
        p[9] = 17                // UDP
        System.arraycopy(srcIp, 0, p, 12, 4)
        System.arraycopy(dstIp, 0, p, 16, 4)
        putU16(p, 10, ipChecksum(p, 20))
        putU16(p, 20, srcPort)
        putU16(p, 22, dstPort)
        putU16(p, 24, 8 + payload.size)
        System.arraycopy(payload, 0, p, 28, payload.size)
        return p
    }

    /** Builds the reply packet for [q] carrying [dnsResponse]; source and destination are swapped. */
    fun reply(q: Query, dnsResponse: ByteArray): ByteArray =
        wrapUdp(q.dstIp, q.srcIp, q.dstPort, q.srcPort, dnsResponse)

    fun ipChecksum(buf: ByteArray, len: Int): Int {
        var sum = 0
        var i = 0
        while (i < len) {
            if (i != 10) sum += u16(buf, i) // skip the checksum field itself
            i += 2
        }
        while (sum shr 16 != 0) sum = (sum and 0xffff) + (sum shr 16)
        return sum.inv() and 0xffff
    }

    private fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xff) shl 8) or (b[o + 1].toInt() and 0xff)
    private fun putU16(b: ByteArray, o: Int, v: Int) {
        b[o] = (v shr 8).toByte(); b[o + 1] = v.toByte()
    }
}
