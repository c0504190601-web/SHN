package com.kdroid.filter;

import android.content.Intent;
import android.net.*;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * VPN מקומי שחל רק על גוגל מפות. רק כתובת ה-DNS המדומה מנותבת אליו,
 * כך ששאר התעבורה של המפות ממשיכה ישירות ברשת הרגילה.
 */
public class MapsVpnService extends VpnService {
 static final String ACTION_START = "com.kdroid.filter.MAPS_START", ACTION_STOP = "com.kdroid.filter.MAPS_STOP";
 static final String VPN_ADDR = "10.111.0.1", DNS_ADDR = "10.111.0.2";
 static final String[] FALLBACK_DNS = {"8.8.8.8", "1.1.1.1"};

 ParcelFileDescriptor tun;
 Thread reader;
 ExecutorService pool;
 volatile Set<String> list = Collections.emptySet();

 @Override public int onStartCommand(Intent intent, int flags, int id) {
  String action = intent == null ? null : intent.getAction();
  if (ACTION_STOP.equals(action) || !MapsFilter.enabled(this)) { shutdown(); stopSelf(); return START_NOT_STICKY; }
  list = MapsFilter.domains(this); // רענון הרשימה גם כשה-VPN כבר רץ
  if (tun == null && !establish()) { stopSelf(); return START_NOT_STICKY; }
  return START_STICKY;
 }

 synchronized boolean establish() {
  Builder b = new Builder().setSession("סינון גוגל מפות").setMtu(1500)
   .addAddress(VPN_ADDR, 32).addDnsServer(DNS_ADDR).addRoute(DNS_ADDR, 32).setBlocking(true);
  int apps = 0;
  for (String p : MapsFilter.installedMaps(this)) try { b.addAllowedApplication(p); apps++; } catch (Exception ignored) {}
  // בלי אפליקציה מותרת ה-VPN היה חל על כל המכשיר — לא מפעילים במצב כזה
  if (apps == 0) return false;
  if (Build.VERSION.SDK_INT >= 29) b.setMetered(false);
  try { tun = b.establish(); } catch (Exception e) { tun = null; }
  if (tun == null) return false;
  pool = Executors.newFixedThreadPool(6);
  final ParcelFileDescriptor fd = tun;
  reader = new Thread(() -> loop(fd), "maps-dns");
  reader.start();
  return true;
 }

 void loop(ParcelFileDescriptor fd) {
  FileInputStream in = new FileInputStream(fd.getFileDescriptor());
  FileOutputStream out = new FileOutputStream(fd.getFileDescriptor());
  byte[] buf = new byte[32767];
  try {
   while (!Thread.interrupted()) {
    int n = in.read(buf);
    if (n <= 0) continue;
    final byte[] pkt = Arrays.copyOf(buf, n);
    ExecutorService p = pool;
    if (p != null) try { p.execute(() -> handle(pkt, out)); } catch (RejectedExecutionException ignored) {}
   }
  } catch (Exception ignored) {}
 }

 /** מטפל בחבילת IPv4/UDP לפורט 53. */
 void handle(byte[] pkt, FileOutputStream out) {
  try {
   if (pkt.length < 28 || (pkt[0] >> 4 & 0xF) != 4 || pkt[9] != 17) return;
   int ihl = (pkt[0] & 0xF) * 4;
   if (pkt.length < ihl + 8 || u16(pkt, ihl + 2) != 53) return;
   int dnsOff = ihl + 8;
   byte[] query = Arrays.copyOfRange(pkt, dnsOff, pkt.length);
   if (query.length < 12) return;

   int[] qEnd = new int[1];
   String host = qname(query, qEnd);
   byte[] answer;
   if (host != null && MapsFilter.blocked(host, list)) answer = nxdomain(query, qEnd[0]);
   else answer = forward(query);
   if (answer == null) return;

   byte[] resp = ipUdp(pkt, ihl, answer);
   synchronized (out) { out.write(resp); }
  } catch (Exception ignored) {}
 }

 static int u16(byte[] b, int o) { return (b[o] & 0xFF) << 8 | (b[o + 1] & 0xFF); }

 /** קורא את שם הדומיין מהשאלה הראשונה. qEnd מקבל את סוף קטע השאלה. */
 static String qname(byte[] q, int[] qEnd) {
  if (u16(q, 4) < 1) return null;
  StringBuilder sb = new StringBuilder();
  int i = 12;
  while (i < q.length) {
   int len = q[i] & 0xFF;
   if (len == 0) { i++; break; }
   if ((len & 0xC0) != 0 || i + 1 + len > q.length) return null;
   if (sb.length() > 0) sb.append('.');
   for (int k = 0; k < len; k++) sb.append((char) (q[i + 1 + k] & 0xFF));
   i += 1 + len;
  }
  if (i + 4 > q.length) return null;
  qEnd[0] = i + 4;
  return sb.toString();
 }

 /** תשובת "דומיין לא קיים" עם השאלה המקורית בלבד. */
 static byte[] nxdomain(byte[] q, int qEnd) {
  byte[] r = Arrays.copyOf(q, qEnd);
  r[2] = (byte) (0x80 | (q[2] & 0x79));  // QR=1, שמירת Opcode ו-RD
  r[3] = (byte) 0x83;                    // RA=1, RCODE=3 (NXDOMAIN)
  r[4] = 0; r[5] = 1;                    // שאלה אחת
  for (int k = 6; k < 12; k++) r[k] = 0; // אין תשובות / רשומות נוספות
  return r;
 }

 /** מעביר את השאילתה לשרת ה-DNS של הרשת הפעילה (כך שהסינון הקיים ברשת, כמו נטפרי, נשמר). */
 byte[] forward(byte[] query) {
  Network net = null;
  List<InetAddress> servers = new ArrayList<>();
  try {
   ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
   Network active = cm.getActiveNetwork();
   List<Network> cands = new ArrayList<>();
   if (active != null) cands.add(active);
   cands.addAll(Arrays.asList(cm.getAllNetworks()));
   for (Network n : cands) {
    NetworkCapabilities caps = cm.getNetworkCapabilities(n);
    if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
    LinkProperties lp = cm.getLinkProperties(n);
    if (lp == null || lp.getDnsServers().isEmpty()) continue;
    net = n; servers.addAll(lp.getDnsServers());
    break;
   }
  } catch (Exception ignored) {}
  if (servers.isEmpty()) for (String s : FALLBACK_DNS) try { servers.add(InetAddress.getByName(s)); } catch (Exception ignored) {}

  for (InetAddress server : servers) {
   try (DatagramSocket s = new DatagramSocket()) {
    protect(s);
    if (net != null) try { net.bindSocket(s); } catch (Exception ignored) {}
    s.setSoTimeout(4000);
    s.send(new DatagramPacket(query, query.length, server, 53));
    byte[] buf = new byte[4096];
    DatagramPacket p = new DatagramPacket(buf, buf.length);
    s.receive(p);
    if (p.getLength() >= 12 && buf[0] == query[0] && buf[1] == query[1]) return Arrays.copyOf(buf, p.getLength());
   } catch (Exception ignored) {}
  }
  return null;
 }

 /** בונה חבילת IPv4/UDP חוזרת לאפליקציה. */
 static byte[] ipUdp(byte[] req, int ihl, byte[] payload) {
  int total = 20 + 8 + payload.length;
  byte[] r = new byte[total];
  r[0] = 0x45; r[2] = (byte) (total >> 8); r[3] = (byte) total;
  r[6] = 0x40; r[8] = 64; r[9] = 17;
  System.arraycopy(req, 16, r, 12, 4); // מקור = היעד המקורי (שרת ה-DNS המדומה)
  System.arraycopy(req, 12, r, 16, 4); // יעד = האפליקציה
  int sum = 0;
  for (int k = 0; k < 20; k += 2) sum += u16(r, k);
  while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
  sum = ~sum & 0xFFFF;
  r[10] = (byte) (sum >> 8); r[11] = (byte) sum;
  r[20] = req[ihl + 2]; r[21] = req[ihl + 3]; // פורט מקור 53
  r[22] = req[ihl];     r[23] = req[ihl + 1]; // פורט היעד = פורט המקור של האפליקציה
  int ulen = 8 + payload.length;
  r[24] = (byte) (ulen >> 8); r[25] = (byte) ulen; // checksum 0 = ללא בדיקה (מותר ב-IPv4)
  System.arraycopy(payload, 0, r, 28, payload.length);
  return r;
 }

 synchronized void shutdown() {
  if (reader != null) reader.interrupt();
  reader = null;
  if (pool != null) pool.shutdownNow();
  pool = null;
  if (tun != null) try { tun.close(); } catch (Exception ignored) {}
  tun = null;
 }

 @Override public void onRevoke() { shutdown(); super.onRevoke(); }
 @Override public void onDestroy() { shutdown(); super.onDestroy(); }
}
