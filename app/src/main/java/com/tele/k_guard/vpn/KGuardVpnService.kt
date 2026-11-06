package com.tele.k_guard.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tele.k_guard.MainActivity
import com.tele.k_guard.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.Executors

/**
 * On-device DNS filter with live session counter notification.
 */
class KGuardVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val forwarders = Executors.newFixedThreadPool(8)
    private val reporter = WebBlockReporter()
    @Volatile private var running = false

    override fun onCreate() {
        super.onCreate()
        QueryMonitor.init(this)
        Log.d("KGuardAI", "KGuardVpnService: onCreate initiated")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.d("KGuardAI", "KGuardVpnService: stop action received")
            VpnConfigStore(this).setEnabled(false)
            stopFiltering()
            return START_NOT_STICKY
        }
        if (intent == null && !VpnConfigStore(this).load().enabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        if (!running) startFiltering()
        return START_STICKY
    }

    override fun onRevoke() {
        Log.d("KGuardAI", "KGuardVpnService: onRevoke")
        VpnConfigStore(this).setEnabled(false)
        stopFiltering()
    }

    override fun onDestroy() {
        Log.d("KGuardAI", "KGuardVpnService: onDestroy")
        stopFiltering()
        scope.cancel()
        forwarders.shutdownNow()
        super.onDestroy()
    }

    private fun startFiltering() {
        Log.d("KGuardAI", "KGuardVpnService: starting live DNS filtering tunnel...")
        val store = VpnConfigStore(this)
        val filter = DomainFilter.load(this)
        val builder = Builder()
            .setSession("K-Guard Live Web Protection")
            .addAddress(TUN_ADDRESS, 32)
            .addDnsServer(DNS_ADDRESS)
            .addRoute(DNS_ADDRESS, 32)
            .setBlocking(true)
        val pfd = runCatching { builder.establish() }.getOrNull()
        if (pfd == null) {
            Log.e("KGuardAI", "KGuardVpnService: Failed to establish VPN interface")
            stopFiltering()
            return
        }
        tun = pfd
        running = true
        VpnState.setRunning(true)
        store.setEnabled(true)

        scope.launch {
            val input = FileInputStream(pfd.fileDescriptor)
            val output = FileOutputStream(pfd.fileDescriptor)
            val buf = ByteArray(32767)
            while (running) {
                val n = try { input.read(buf) } catch (e: Exception) { break }
                if (n <= 0) continue
                val q = DnsPacket.parseQuery(buf, n) ?: continue
                handle(q, filter, store, output)
            }
        }
    }

    private fun handle(q: DnsPacket.Query, filter: DomainFilter, store: VpnConfigStore, out: FileOutputStream) {
        val started = System.nanoTime()
        val name = q.name
        val type = DnsPacket.typeName(q.qtype ?: 0)
        val cfg = store.load()
        val verdict = if (name == null) Verdict(false) else filter.evaluate(name, cfg.policy)

        if (verdict.blocked && name != null) {
            write(out, DnsPacket.reply(q, DnsPacket.nxDomain(q.dns)))
            val explanation = DomainFilter.explain(verdict, cfg.policy)
            Log.w("KGuardAI", "KGuardVpnService: BLOCKED domain=$name, category=${verdict.category?.label}, explanation=$explanation")
            VpnState.recordBlock(name, verdict.category?.label ?: "Blocked by a parent", explanation)
            notifyBlocked(explanation)
            val childId = cfg.childId
            if (childId != null) scope.launch { reporter.report(childId, verdict.category, name) }
            QueryMonitor.record(name, type, verdict, QueryMonitor.Outcome.BLOCKED, elapsedMs(started))
            return
        }

        val safeTarget = if (name != null) SafeSearch.targetFor(name, cfg.policy.ageProfile) else null
        forwarders.execute {
            val upstreamStarted = System.nanoTime()
            val answer = if (safeTarget != null) safeAnswer(q.dns, safeTarget) else forward(q.dns)
            val upstreamMs = elapsedMs(upstreamStarted)
            if (answer == null) {
                if (name != null) QueryMonitor.record(name, type, verdict, QueryMonitor.Outcome.UNANSWERED, 0)
                return@execute
            }
            write(out, DnsPacket.reply(q, answer))
            if (name != null) {
                val outcome = if (safeTarget != null) QueryMonitor.Outcome.REWRITTEN else QueryMonitor.Outcome.ALLOWED
                QueryMonitor.record(name, type, verdict, outcome, (elapsedMs(started) - upstreamMs).coerceAtLeast(0))
            }
        }
    }

    private fun safeAnswer(dns: ByteArray, target: String): ByteArray? {
        val type = DnsPacket.parseQType(dns) ?: return null
        return when (type) {
            1, 28 -> {
                val upstream = forward(DnsPacket.buildQuery(target, type)) ?: return null
                DnsPacket.cnameAnswer(dns, target, DnsPacket.parseAddresses(upstream, type))
            }
            64, 65 -> DnsPacket.noData(dns)
            else -> forward(dns)
        }
    }

    private fun elapsedMs(startNanos: Long) = (System.nanoTime() - startNanos) / 1_000_000

    private var lastNotice = 0L

    private fun notifyBlocked(explanation: String) {
        val now = System.currentTimeMillis()
        if (now - lastNotice < 30_000) return
        lastNotice = now
        com.tele.k_guard.service.KGuardMessagingService.showAlert(this, "K-Guard blocked a website", explanation)
    }

    private fun forward(dns: ByteArray): ByteArray? = runCatching {
        DatagramSocket().use { s ->
            protect(s)
            s.soTimeout = 4000
            s.send(DatagramPacket(dns, dns.size, InetAddress.getByName(UPSTREAM_DNS), 53))
            val resp = ByteArray(4096)
            val p = DatagramPacket(resp, resp.size)
            s.receive(p)
            resp.copyOf(p.length)
        }
    }.getOrNull()

    @Synchronized
    private fun write(out: FileOutputStream, packet: ByteArray) {
        runCatching { out.write(packet) }
    }

    private fun stopFiltering() {
        Log.d("KGuardAI", "KGuardVpnService: stopping filtering tunnel...")
        running = false
        QueryMonitor.flush()
        VpnState.setRunning(false)
        runCatching { tun?.close() }
        tun = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Web protection", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Web protection is on")
            .setContentText("Harmful websites are blocked on this phone.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    companion object {
        const val ACTION_STOP = "com.tele.k_guard.vpn.STOP"
        private const val TUN_ADDRESS = "10.0.0.2"
        private const val DNS_ADDRESS = "10.0.0.1"
        private const val UPSTREAM_DNS = "1.1.1.1"
        private const val CHANNEL = "web_protection"
        private const val NOTIFICATION_ID = 4711

        fun start(context: Context) {
            Log.d("KGuardAI", "KGuardVpnService: start requested")
            val i = Intent(context, KGuardVpnService::class.java)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            Log.d("KGuardAI", "KGuardVpnService: stop requested")
            context.startService(Intent(context, KGuardVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
