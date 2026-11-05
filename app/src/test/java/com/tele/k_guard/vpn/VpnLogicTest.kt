package com.tele.k_guard.vpn

import com.tele.k_guard.data.model.AgeProfile
import com.tele.k_guard.data.model.SafetyCategory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnLogicTest {

    private val filter = DomainFilter(
        mapOf(
            SafetyCategory.SEXUAL to setOf("adult.example"),
            SafetyCategory.VIOLENCE to setOf("gore.example"),
            SafetyCategory.SCAM to setOf("scam.example")
        )
    )

    private fun policy(age: AgeProfile, rules: Map<String, String> = emptyMap(), block: Set<String> = emptySet(), allow: Set<String> = emptySet()) =
        VpnPolicy(age, rules, block, allow)

    @Test fun subdomainsMatchButLookalikesDoNot() {
        val p = policy(AgeProfile.KIDS_3_7)
        assertTrue(filter.evaluate("www.adult.example", p).blocked)
        assertTrue(filter.evaluate("ADULT.example.", p).blocked)
        assertFalse(filter.evaluate("notadult.example", p).blocked)
        assertFalse(filter.evaluate("example", p).blocked)
    }

    @Test fun defaultsDependOnAge() {
        assertTrue(filter.evaluate("gore.example", policy(AgeProfile.PRETEENS_8_12)).blocked)
        assertTrue(filter.evaluate("gore.example", policy(AgeProfile.TEENS_13_17)).blocked)
        assertFalse(filter.evaluate("gore.example", policy(AgeProfile.ADULTS_18_PLUS)).blocked)
        assertTrue(filter.evaluate("scam.example", policy(AgeProfile.ADULTS_18_PLUS)).blocked)
    }

    @Test fun parentCanAllowViolenceButNotSexualForMinors() {
        val rules = mapOf("violence" to "allow", "sexual" to "allow")
        assertFalse(filter.evaluate("gore.example", policy(AgeProfile.TEENS_13_17, rules)).blocked)
        assertTrue(filter.evaluate("adult.example", policy(AgeProfile.TEENS_13_17, rules)).blocked)
        assertFalse(filter.evaluate("adult.example", policy(AgeProfile.ADULTS_18_PLUS, rules)).blocked)
    }

    @Test fun blockRuleTightensAdultProfile() {
        val v = filter.evaluate("gore.example", policy(AgeProfile.ADULTS_18_PLUS, mapOf("violence" to "block")))
        assertTrue(v.blocked); assertEquals(SafetyCategory.VIOLENCE, v.category)
    }

    @Test fun customListsWinOverCategories() {
        val p = policy(AgeProfile.KIDS_3_7, block = setOf("games.example"), allow = setOf("adult.example"))
        assertTrue(filter.evaluate("games.example", p).custom)
        assertFalse(filter.evaluate("adult.example", p).blocked)
    }

    @Test fun parsesListsWithComments() {
        assertEquals(setOf("a.com", "b.org"), DomainFilter.parseList("# hi\nA.com\n\nb.org # note\n"))
    }

    private fun query(name: String): ByteArray {
        val head = byteArrayOf(0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0)
        val q = name.split('.').flatMap { l -> listOf(l.length.toByte()) + l.map { it.code.toByte() } } + listOf<Byte>(0, 0, 1, 0, 1)
        return head + q.toByteArray()
    }

    @Test fun parsesDnsNameAndBuildsNxDomain() {
        val dns = query("Www.Adult.Example")
        assertEquals("www.adult.example", DnsPacket.parseName(dns))
        val nx = DnsPacket.nxDomain(dns)
        assertEquals(0x83, nx[3].toInt() and 0xff)
        assertTrue(nx[2].toInt() and 0x80 != 0)
        assertArrayEquals(byteArrayOf(0x12, 0x34), nx.copyOfRange(0, 2))
        assertEquals(dns.size, nx.size)
    }

    @Test fun roundTripsThroughIpv4Packet() {
        val dns = query("adult.example")
        val pkt = DnsPacket.wrapUdp(byteArrayOf(10, 0, 0, 2), byteArrayOf(10, 0, 0, 1), 40000, 53, dns)
        val q = DnsPacket.parseQuery(pkt, pkt.size)
        assertNotNull(q)
        assertEquals("adult.example", q!!.name)
        assertEquals(40000, q.srcPort)
        val reply = DnsPacket.reply(q, DnsPacket.nxDomain(dns))
        val back = DnsPacket.parseQuery(reply, reply.size)
        assertNull(back) // reply goes to port 40000, not 53, so it is not treated as a query
        assertEquals(0, DnsPacket.ipChecksum(reply, 20).let { c -> // checksum over a valid header is zero when included
            var sum = 0; var i = 0
            while (i < 20) { sum += ((reply[i].toInt() and 0xff) shl 8) or (reply[i + 1].toInt() and 0xff); i += 2 }
            while (sum shr 16 != 0) sum = (sum and 0xffff) + (sum shr 16)
            (sum.inv() and 0xffff)
        })
    }

    @Test fun ignoresNonDnsAndMalformedPackets() {
        assertNull(DnsPacket.parseQuery(ByteArray(10), 10))
        val tcpLike = DnsPacket.wrapUdp(byteArrayOf(10, 0, 0, 2), byteArrayOf(10, 0, 0, 1), 1, 80, query("a.com"))
        assertNull(DnsPacket.parseQuery(tcpLike, tcpLike.size))
        assertNull(DnsPacket.parseName(ByteArray(5)))
    }
}

class VpnExplainTest {
    private val filter = DomainFilter(
        mapOf(SafetyCategory.SEXUAL to setOf("adult.example", "wikipedia.org"), SafetyCategory.VIOLENCE to setOf("gore.example")),
        educational = setOf("wikipedia.org")
    )

    @Test fun educationalSiteIsAllowedEvenIfListed() {
        val p = VpnPolicy(AgeProfile.KIDS_3_7)
        val v = filter.evaluate("en.wikipedia.org", p)
        assertFalse(v.blocked); assertEquals(Reason.ALLOWED_EDUCATIONAL, v.reason)
        assertTrue(filter.evaluate("en.wikipedia.org", p.copy(educationalAllow = false)).blocked)
    }

    @Test fun reasonsAndExplanations() {
        val kid = VpnPolicy(AgeProfile.PRETEENS_8_12)
        val v = filter.evaluate("gore.example", kid)
        assertEquals(Reason.BLOCKED_AGE, v.reason)
        assertTrue(DomainFilter.explain(v, kid).contains("ages 8"))
        val teenAllow = VpnPolicy(AgeProfile.TEENS_13_17, rules = mapOf("sexual" to "allow", "violence" to "allow"))
        assertEquals(Reason.BLOCKED_LOCKED, filter.evaluate("adult.example", teenAllow).reason)
        assertEquals(Reason.ALLOWED_BY_RULE, filter.evaluate("gore.example", teenAllow).reason)
        assertEquals(Reason.BLOCKED_RULE, filter.evaluate("gore.example", VpnPolicy(AgeProfile.ADULTS_18_PLUS, mapOf("violence" to "block"))).reason)
    }

    @Test fun effectiveMatrixMatchesDefaults() {
        assertTrue(DomainFilter.effective(SafetyCategory.DRUGS, VpnPolicy(AgeProfile.TEENS_13_17)).blocked)
        assertFalse(DomainFilter.effective(SafetyCategory.PROFANITY, VpnPolicy(AgeProfile.TEENS_13_17)).blocked)
        assertEquals(Source.RULE, DomainFilter.effective(SafetyCategory.DRUGS, VpnPolicy(AgeProfile.TEENS_13_17, mapOf("drugs" to "allow"))).source)
    }
}
