package io.github.pixelsuite;

import java.io.IOException;

abstract class VpnTunnel {
    interface Protector { boolean protect(java.net.Socket socket); boolean protect(java.net.DatagramSocket socket); }
    interface Sink { void ip(byte[] data, int off, int len); void closed(String why); }

    String localIp; String[] dns = new String[0];
    volatile long lastRx;

    abstract String protocol();
    abstract void start(Sink sink);
    abstract void sendIp(byte[] buf, int len) throws IOException;
    abstract void echo() throws IOException;
    abstract boolean closed();
    abstract void close();
}
