package io.github.pixelsuite;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.*;

final class SstpClient extends VpnTunnel {

    private static final int LCP=0xC021,PAP=0xC023,CHAP=0xC223,IPCP=0x8021,IPV4=0x0021;
    private static final SecureRandom RANDOM=new SecureRandom();

    String server;
    String protocol(){return "SSTP";}
    private volatile boolean closed,established;
    private volatile Sink sink;
    private Socket raw;private SSLSocket ssl;private InputStream in;private OutputStream out;
    private final Object writeLock=new Object();
    private final LinkedBlockingQueue<Object> queue=new LinkedBlockingQueue<Object>();
    private byte[] certSha256,certSha1,nonce;private int hashBits;
    private byte[] hlak=new byte[32];
    private int magic,lcpId;

    void connect(String ip,int port,String host,String user,String pass,Protector protector)throws IOException{
        server=host;
        raw=new Socket();
        try{raw.setReceiveBufferSize(4<<20);raw.setSendBufferSize(1<<20);}catch(Exception ignored){}
        raw.bind(null);
        raw.connect(new InetSocketAddress(ip,port),4000);raw.setTcpNoDelay(true);raw.setSoTimeout(12000);
        try{
            SSLContext tls=SSLContext.getInstance("TLS");
            tls.init(null,new TrustManager[]{new X509TrustManager(){
                public void checkClientTrusted(X509Certificate[] c,String a){}
                public void checkServerTrusted(X509Certificate[] c,String a){}
                public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}}},RANDOM);
            ssl=(SSLSocket)tls.getSocketFactory().createSocket(raw,host,port,true);
            ssl.startHandshake();
            byte[] der=ssl.getSession().getPeerCertificates()[0].getEncoded();
            certSha256=MessageDigest.getInstance("SHA-256").digest(der);certSha1=MessageDigest.getInstance("SHA-1").digest(der);
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("TLS: "+e.getMessage());}
        in=new BufferedInputStream(ssl.getInputStream(),32768);out=ssl.getOutputStream();

        String request="SSTP_DUPLEX_POST /sra_{BA195980-CD49-458b-9E23-C84EE0ADCD75}/ HTTP/1.1\r\nHost: "+host+"\r\nContent-Length: 18446744073709551615\r\nSSTPCORRELATIONID: {"+UUID.randomUUID().toString().toUpperCase()+"}\r\n\r\n";
        synchronized(writeLock){out.write(request.getBytes("US-ASCII"));out.flush();}
        String status=httpHeader();
        if(!status.contains(" 200"))throw new IOException("Server refused SSTP ("+status.trim()+")");
        raw.setSoTimeout(0);lastRx=now();
        Thread reader=new Thread(new Runnable(){public void run(){readLoop();}},"PixelSuite-SSTP");reader.setDaemon(true);reader.start();

        send(new byte[]{0x10,0x01,0x00,0x0E,0x00,0x01,0x00,0x01,0x00,0x01,0x00,0x06,0x00,0x01});
        ArrayList<byte[]> early=new ArrayList<byte[]>();long deadline=now()+10000;
        while(nonce==null){
            Object o=take(deadline,"Server didn't answer");
            if(o instanceof byte[]){early.add((byte[])o);continue;}
            byte[] c=((Control)o).body;int type=u16(c,0);
            if(type==3)throw new IOException("Server refused the call");
            if(type==2){
                for(int i=4;i+4<=c.length;){int id=c[i+1]&255,len=u16(c,i+2);if(len<4||i+len>c.length)break;if(id==4&&len>=40){hashBits=c[i+7]&255;nonce=new byte[32];System.arraycopy(c,i+8,nonce,0,32);}i+=len;}
                if(nonce==null){nonce=new byte[32];hashBits=2;}
            }
        }
        negotiate(user,pass,early);
        established=true;
    }

    void start(Sink sink){this.sink=sink;}
    boolean closed(){return closed;}

    private static long now(){return android.os.SystemClock.elapsedRealtime();}

    private String httpHeader()throws IOException{
        StringBuilder all=new StringBuilder();int state=0;
        while(state<4){int b=in.read();if(b<0)throw new IOException("Connection closed");all.append((char)b);if(all.length()>8192)throw new IOException("Bad HTTP reply");
            state=(b=='\r'&&(state==0||state==2))?state+1:(b=='\n'&&(state==1||state==3))?state+1:b=='\r'?1:0;}
        int line=all.indexOf("\r\n");return line<0?all.toString():all.substring(0,line);
    }

    private static final class Control{final byte[] body;Control(byte[] b){body=b;}}
    private static final class Failure{final String why;Failure(String w){why=w;}}

    private Object take(long deadline,String what)throws IOException{
        while(true){
            long left=deadline-now();if(left<=0)throw new IOException(what);
            Object o;try{o=queue.poll(Math.min(left,500),TimeUnit.MILLISECONDS);}catch(InterruptedException e){throw new IOException("Stopped");}
            if(o instanceof Failure)throw new IOException(((Failure)o).why);
            if(o!=null)return o;
            if(closed)throw new IOException("Stopped");
        }
    }

    private void readLoop(){
        String why="Connection closed";
        try{
            byte[] h=new byte[4];
            while(!closed){
                full(h,4);
                if((h[0]&255)!=0x10)throw new IOException("Bad SSTP packet");
                int len=((h[2]&0x0F)<<8)|(h[3]&255);if(len<4)throw new IOException("Bad SSTP length");
                byte[] b=new byte[len-4];full(b,b.length);lastRx=now();
                if((h[1]&1)!=0)control(b);else ppp(b);
            }
        }catch(IOException e){if(e.getMessage()!=null)why=e.getMessage();}
        finish(why);
    }

    private void full(byte[] b,int n)throws IOException{int o=0;while(o<n){int r=in.read(b,o,n-o);if(r<0)throw new IOException("Server closed the connection");o+=r;}}

    private void control(byte[] b)throws IOException{
        if(b.length<2)return;int type=u16(b,0);
        if(type==8){send(new byte[]{0x10,0x01,0x00,0x08,0x00,0x09,0x00,0x00});return;}
        if(type==9)return;
        if(type==6){send(new byte[]{0x10,0x01,0x00,0x08,0x00,0x07,0x00,0x00});throw new IOException("Server ended the session");}
        if(type==5)throw new IOException("Server aborted the session");
        queue.offer(new Control(b));
    }

    private void ppp(byte[] b)throws IOException{
        int off=0;if(b.length>=2&&(b[0]&255)==0xFF&&(b[1]&255)==0x03)off=2;
        if(b.length-off<2)return;int proto=u16(b,off);
        if(established){
            if(proto==IPV4){Sink s=sink;if(s!=null)s.ip(b,off+2,b.length-off-2);return;}
            if(proto==LCP&&b.length-off>=6){int code=b[off+2]&255,id=b[off+3]&255;
                if(code==9){byte[] reply=new byte[Math.max(8,u16(b,off+4))];System.arraycopy(b,off+2,reply,0,Math.min(reply.length,b.length-off-2));reply[0]=10;reply[1]=(byte)id;put32(reply,4,magic);sendPpp(LCP,reply);return;}
                if(code==5){sendCp(LCP,6,id,new byte[0]);throw new IOException("Server closed the link");}
                return;}
            if(proto==IPCP||proto==CHAP||proto==PAP)return;
            protocolReject(b,off);return;
        }
        byte[] frame=new byte[b.length-off];System.arraycopy(b,off,frame,0,frame.length);queue.offer(frame);
    }

    private void finish(String why){
        if(closed)return;closed=true;queue.offer(new Failure(why));
        try{if(ssl!=null)ssl.close();else if(raw!=null)raw.close();}catch(IOException ignored){}
        Sink s=sink;if(s!=null)s.closed(why);
    }

    void close(){
        if(closed)return;
        try{if(established){sendCp(LCP,5,++lcpId&255,new byte[0]);send(new byte[]{0x10,0x01,0x00,0x08,0x00,0x06,0x00,0x00});}}catch(Exception ignored){}
        sink=null;finish("Disconnected");
    }

    void echo()throws IOException{send(new byte[]{0x10,0x01,0x00,0x08,0x00,0x08,0x00,0x00});}

    private void negotiate(String user,String pass,ArrayList<byte[]> early)throws IOException{
        magic=RANDOM.nextInt();if(magic==0)magic=1;
        boolean sendMagic=true,lcpOurs=false,lcpTheirs=false,authed=false,connectedSent=false,ipcpOurs=false,ipcpTheirs=false;
        boolean wantDns1=true,wantDns2=true;byte[] addr=new byte[4],dns1=new byte[4],dns2=new byte[4];
        int auth=0,papId=0,ipcpId=0,lcpReq=0;long lcpAt=0,papAt=0,ipcpAt=0;
        boolean mschap=mschapAvailable();
        long deadline=now()+20000;String stage="Link setup timed out";
        while(true){
            long t=now();
            if(!lcpOurs&&t-lcpAt>2500){lcpReq=++lcpId&255;sendCp(LCP,1,lcpReq,sendMagic?opt(5,int4(magic)):new byte[0]);lcpAt=t;}
            if(lcpOurs&&lcpTheirs&&!authed){
                stage="Sign-in timed out";
                if(auth==0)authed=true;
                else if(auth==PAP&&t-papAt>3000){papId=(papId+1)&255;byte[] u=user.getBytes("UTF-8"),p=pass.getBytes("UTF-8");byte[] d=new byte[6+u.length+p.length];d[0]=1;d[1]=(byte)papId;put16(d,2,d.length);d[4]=(byte)u.length;System.arraycopy(u,0,d,5,u.length);d[5+u.length]=(byte)p.length;System.arraycopy(p,0,d,6+u.length,p.length);sendPpp(PAP,d);papAt=t;}
            }
            if(authed&&!connectedSent){sendConnected();connectedSent=true;stage="Address setup timed out";}
            if(authed&&!ipcpOurs&&t-ipcpAt>2500){ipcpId=(ipcpId+1)&255;
                ByteArrayOutputStream o=new ByteArrayOutputStream();o.write(opt(3,addr));if(wantDns1)o.write(opt(129,dns1));if(wantDns2)o.write(opt(131,dns2));
                sendCp(IPCP,1,ipcpId,o.toByteArray());ipcpAt=t;}
            if(ipcpOurs&&ipcpTheirs)break;

            byte[] f;
            if(!early.isEmpty())f=early.remove(0);
            else{Object o;try{o=take(deadline,stage);}catch(IOException e){throw new IOException(e.getMessage());}if(!(o instanceof byte[]))continue;f=(byte[])o;}
            if(f.length<6)continue;
            int proto=u16(f,0),code=f[2]&255,id=f[3]&255,len=Math.min(u16(f,4),f.length-2);
            if(len<4)continue;
            if(proto==LCP){
                if(code==1){
                    ByteArrayOutputStream rej=new ByteArrayOutputStream(),nak=new ByteArrayOutputStream();int want=0;
                    for(int i=6;i+2<=len+2;){int ot=f[i]&255,ol=f[i+1]&255;if(ol<2||i+ol>len+2)break;
                        if(ot==3&&ol>=4){int ap=u16(f,i+2);
                            if(ap==PAP)want=PAP;
                            else if(ap==CHAP&&ol>=5&&(f[i+4]&255)==0x81&&mschap)want=CHAP;
                            else nak.write(mschap?new byte[]{3,5,(byte)0xC2,0x23,(byte)0x81}:new byte[]{3,4,(byte)0xC0,0x23});
                        }else if(ot!=1&&ot!=5)rej.write(f,i,ol);
                        i+=ol;}
                    if(rej.size()>0)sendCp(LCP,4,id,rej.toByteArray());
                    else if(nak.size()>0)sendCp(LCP,3,id,nak.toByteArray());
                    else{byte[] ack=new byte[len-4];System.arraycopy(f,6,ack,0,ack.length);sendCp(LCP,2,id,ack);lcpTheirs=true;auth=want;}
                }else if(code==2&&id==lcpReq)lcpOurs=true;
                else if(code==3&&id==lcpReq){magic=RANDOM.nextInt();if(magic==0)magic=1;lcpAt=0;}
                else if(code==4&&id==lcpReq){sendMagic=false;lcpAt=0;}
                else if(code==5){sendCp(LCP,6,id,new byte[0]);throw new IOException("Server closed the link");}
                else if(code==9){byte[] r=new byte[Math.max(8,len)];System.arraycopy(f,2,r,0,Math.min(r.length,f.length-2));r[0]=10;put32(r,4,magic);sendPpp(LCP,r);}
            }else if(proto==PAP){
                if(code==2)authed=true;else if(code==3)throw new IOException("Server rejected the sign-in");
            }else if(proto==CHAP){
                if(code==1&&len>=21&&(f[6]&255)==16){
                    byte[] challenge=new byte[16];System.arraycopy(f,7,challenge,0,16);
                    byte[] response=mschapResponse(challenge,user,pass);byte[] u=user.getBytes("UTF-8");
                    byte[] d=new byte[5+49+u.length];d[0]=2;d[1]=(byte)id;put16(d,2,d.length);d[4]=49;System.arraycopy(response,0,d,5,49);System.arraycopy(u,0,d,54,u.length);
                    sendPpp(CHAP,d);
                }else if(code==3)authed=true;
                else if(code==4)throw new IOException("Server rejected the sign-in");
            }else if(proto==IPCP){
                if(code==1){
                    ByteArrayOutputStream rej=new ByteArrayOutputStream();
                    for(int i=6;i+2<=len+2;){int ot=f[i]&255,ol=f[i+1]&255;if(ol<2||i+ol>len+2)break;if(ot!=3)rej.write(f,i,ol);i+=ol;}
                    if(rej.size()>0)sendCp(IPCP,4,id,rej.toByteArray());
                    else{byte[] ack=new byte[len-4];System.arraycopy(f,6,ack,0,ack.length);sendCp(IPCP,2,id,ack);ipcpTheirs=true;}
                }else if(id==ipcpId&&(code==2||code==3||code==4)){
                    for(int i=6;i+2<=len+2;){int ot=f[i]&255,ol=f[i+1]&255;if(ol<2||i+ol>len+2)break;
                        if(code==4){if(ot==129)wantDns1=false;else if(ot==131)wantDns2=false;else if(ot==3)throw new IOException("Server gave no address");}
                        else if(ol==6){byte[] v=ot==3?addr:ot==129?dns1:ot==131?dns2:null;if(v!=null)System.arraycopy(f,i+2,v,0,4);}
                        i+=ol;}
                    if(code==2)ipcpOurs=true;else ipcpAt=0;
                }else if(code==5){sendCp(IPCP,6,id,new byte[0]);throw new IOException("Server closed the link");}
            }else if(proto!=IPV4){
                if(lcpOurs&&lcpTheirs)protocolReject(f,0);
            }
        }
        localIp=dotted(addr);
        ArrayList<String> d=new ArrayList<String>();if(wantDns1&&!zero(dns1))d.add(dotted(dns1));if(wantDns2&&!zero(dns2)&&!dotted(dns2).equals(dotted(dns1)))d.add(dotted(dns2));
        dns=d.toArray(new String[0]);
        if(zero(addr))throw new IOException("Server gave no address");
    }

    private void protocolReject(byte[] f,int off)throws IOException{
        int n=Math.min(f.length-off,1400);byte[] d=new byte[4+n];d[0]=8;d[1]=(byte)(++lcpId);put16(d,2,d.length);System.arraycopy(f,off,d,4,n);sendPpp(LCP,d);
    }

    private void sendConnected()throws IOException{
        boolean sha256=(hashBits&2)!=0||(hashBits&1)==0;
        byte[] m=new byte[112];
        m[0]=0x10;m[1]=0x01;m[2]=0x00;m[3]=0x70;m[4]=0x00;m[5]=0x04;m[6]=0x00;m[7]=0x01;
        m[8]=0x00;m[9]=0x03;m[10]=0x00;m[11]=0x68;m[15]=(byte)(sha256?2:1);
        System.arraycopy(nonce,0,m,16,32);
        byte[] cert=sha256?certSha256:certSha1;System.arraycopy(cert,0,m,48,cert.length);
        try{
            String alg=sha256?"HmacSHA256":"HmacSHA1";int size=sha256?32:20;
            byte[] seed="SSTP inner method derived CMK".getBytes("US-ASCII");byte[] input=new byte[seed.length+3];System.arraycopy(seed,0,input,0,seed.length);input[seed.length]=(byte)size;input[seed.length+1]=0;input[seed.length+2]=1;
            byte[] cmk=hmac(alg,sha256?hlak:java.util.Arrays.copyOf(hlak,32),input);
            byte[] mac=hmac(alg,cmk,m);System.arraycopy(mac,0,m,80,mac.length);
        }catch(Exception e){throw new IOException("Crypto binding: "+e.getMessage());}
        send(m);
    }

    private void sendCp(int proto,int code,int id,byte[] options)throws IOException{
        byte[] d=new byte[4+options.length];d[0]=(byte)code;d[1]=(byte)id;put16(d,2,d.length);System.arraycopy(options,0,d,4,options.length);sendPpp(proto,d);
    }

    private void sendPpp(int proto,byte[] payload)throws IOException{
        byte[] p=new byte[8+payload.length];p[0]=0x10;p[1]=0;put16(p,2,p.length&0x0FFF);p[4]=(byte)0xFF;p[5]=0x03;put16(p,6,proto);System.arraycopy(payload,0,p,8,payload.length);send(p);
    }

    void sendIp(byte[] buf,int len)throws IOException{
        int total=len+8;buf[0]=0x10;buf[1]=0;buf[2]=(byte)((total>>8)&0x0F);buf[3]=(byte)total;buf[4]=(byte)0xFF;buf[5]=0x03;buf[6]=0x00;buf[7]=0x21;
        synchronized(writeLock){out.write(buf,0,total);out.flush();}
    }

    private void send(byte[] p)throws IOException{if(closed)throw new IOException("Closed");synchronized(writeLock){out.write(p);out.flush();}}

    private static boolean mschapAvailable(){try{Cipher.getInstance("DES/ECB/NoPadding");return true;}catch(Exception e){return false;}}

    private byte[] mschapResponse(byte[] authChallenge,String user,String pass)throws IOException{
        try{
            byte[] peer=new byte[16];RANDOM.nextBytes(peer);
            byte[] nt=ntResponse(authChallenge,peer,user,pass);
            byte[] r=new byte[49];System.arraycopy(peer,0,r,0,16);System.arraycopy(nt,0,r,24,24);
            byte[] hashHash=md4(md4(pass.getBytes("UTF-16LE")));
            byte[] master=java.util.Arrays.copyOf(sha1(hashHash,nt,"This is the MPPE Master Key".getBytes("US-ASCII")),16);
            byte[] send=startKey(master,true),recv=startKey(master,false);
            System.arraycopy(recv,0,hlak,0,16);System.arraycopy(send,0,hlak,16,16);
            return r;
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("MS-CHAPv2: "+e.getMessage());}
    }

    static byte[] ntResponse(byte[] authChallenge,byte[] peer,String user,String pass)throws Exception{
        byte[] challenge=java.util.Arrays.copyOf(sha1(peer,authChallenge,user.getBytes("UTF-8")),8);
        byte[] hash=java.util.Arrays.copyOf(md4(pass.getBytes("UTF-16LE")),21);
        byte[] out=new byte[24];
        for(int i=0;i<3;i++){byte[] k=new byte[7];System.arraycopy(hash,i*7,k,0,7);Cipher des=Cipher.getInstance("DES/ECB/NoPadding");des.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(desKey(k),"DES"));System.arraycopy(des.doFinal(challenge),0,out,i*8,8);}
        return out;
    }

    static byte[] startKey(byte[] master,boolean clientSend)throws Exception{
        String magic=clientSend?"On the client side, this is the send key; on the server side, it is the receive key.":"On the client side, this is the receive key; on the server side, it is the send key.";
        byte[] pad1=new byte[40],pad2=new byte[40];java.util.Arrays.fill(pad2,(byte)0xF2);
        return java.util.Arrays.copyOf(sha1(master,pad1,magic.getBytes("US-ASCII"),pad2),16);
    }

    private static byte[] desKey(byte[] in){
        byte[] o=new byte[8];
        o[0]=(byte)(in[0]&0xFE);o[1]=(byte)((in[0]<<7)|((in[1]&255)>>>1));o[2]=(byte)((in[1]<<6)|((in[2]&255)>>>2));o[3]=(byte)((in[2]<<5)|((in[3]&255)>>>3));
        o[4]=(byte)((in[3]<<4)|((in[4]&255)>>>4));o[5]=(byte)((in[4]<<3)|((in[5]&255)>>>5));o[6]=(byte)((in[5]<<2)|((in[6]&255)>>>6));o[7]=(byte)(in[6]<<1);
        return o;
    }

    private static byte[] sha1(byte[]... parts)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-1");for(byte[] p:parts)d.update(p);return d.digest();}
    private static byte[] hmac(String alg,byte[] key,byte[] data)throws Exception{Mac m=Mac.getInstance(alg);m.init(new SecretKeySpec(key,alg));return m.doFinal(data);}

    static byte[] md4(byte[] msg){
        int[] s={0x67452301,0xefcdab89,0x98badcfe,0x10325476};
        int n=((msg.length+8)/64+1)*64;byte[] m=new byte[n];System.arraycopy(msg,0,m,0,msg.length);m[msg.length]=(byte)0x80;
        long bits=(long)msg.length*8;for(int i=0;i<8;i++)m[n-8+i]=(byte)(bits>>>(8*i));
        int[] x=new int[16];int[] s1={3,7,11,19},s2={3,5,9,13},s3={3,9,11,15};int[] k2={0,4,8,12,1,5,9,13,2,6,10,14,3,7,11,15},k3={0,8,4,12,2,10,6,14,1,9,5,13,3,11,7,15};
        for(int off=0;off<n;off+=64){
            for(int i=0;i<16;i++)x[i]=(m[off+4*i]&255)|((m[off+4*i+1]&255)<<8)|((m[off+4*i+2]&255)<<16)|((m[off+4*i+3]&255)<<24);
            int a=s[0],b=s[1],c=s[2],d=s[3],t;
            for(int i=0;i<16;i++){t=Integer.rotateLeft(a+((b&c)|(~b&d))+x[i],s1[i%4]);a=d;d=c;c=b;b=t;}
            for(int i=0;i<16;i++){t=Integer.rotateLeft(a+((b&c)|(b&d)|(c&d))+x[k2[i]]+0x5A827999,s2[i%4]);a=d;d=c;c=b;b=t;}
            for(int i=0;i<16;i++){t=Integer.rotateLeft(a+(b^c^d)+x[k3[i]]+0x6ED9EBA1,s3[i%4]);a=d;d=c;c=b;b=t;}
            s[0]+=a;s[1]+=b;s[2]+=c;s[3]+=d;
        }
        byte[] r=new byte[16];for(int i=0;i<4;i++)for(int j=0;j<4;j++)r[i*4+j]=(byte)(s[i]>>>(8*j));return r;
    }

    private static byte[] opt(int type,byte[] v){byte[] o=new byte[2+v.length];o[0]=(byte)type;o[1]=(byte)o.length;System.arraycopy(v,0,o,2,v.length);return o;}
    private static byte[] int4(int v){return new byte[]{(byte)(v>>>24),(byte)(v>>>16),(byte)(v>>>8),(byte)v};}
    private static int u16(byte[] b,int i){return ((b[i]&255)<<8)|(b[i+1]&255);}
    private static void put16(byte[] b,int i,int v){b[i]=(byte)(v>>>8);b[i+1]=(byte)v;}
    private static void put32(byte[] b,int i,int v){b[i]=(byte)(v>>>24);b[i+1]=(byte)(v>>>16);b[i+2]=(byte)(v>>>8);b[i+3]=(byte)v;}
    private static boolean zero(byte[] a){for(byte b:a)if(b!=0)return false;return true;}
    private static String dotted(byte[] a){return (a[0]&255)+"."+(a[1]&255)+"."+(a[2]&255)+"."+(a[3]&255);}
}
