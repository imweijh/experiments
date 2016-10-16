package com.semicomplete.ssl;

import com.semicomplete.Resolver;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateException;
import com.semicomplete.Blame;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import javax.net.ssl.SSLHandshakeException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class SSLDiag {
  /* Diagnose SSL problems
   * 1) TCP connect
   * 2) TLS/SSL protocol negotiation
   * 3) (low priority) TLS/SSL cipher negotiation
   * 4) Certificate trust problems
   * 5) Hostname verification (RFC6125?)
   */

  private KeyStore trustStore;
  private KeyStore keyStore;
  private Resolver resolver = Resolver.SystemResolver;
  private Logger logger = LogManager.getLogger();

  private SSLContext ctx;

  private PeerCertificateDetails peerCertificateResult;

  public SSLDiag(SSLContextBuilder cb) throws KeyManagementException, KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
    cb.setTracker(this::setPeerCertificateResult);
    ctx = cb.build();
  }


  public SSLDiag(KeyStore keyStore, KeyStore trustStore) throws UnknownHostException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException, IOException {
    this.trustStore = trustStore;
    this.keyStore = keyStore;

    SSLContextBuilder ctxbuilder = new SSLContextBuilder();
    ctxbuilder.setTrustStore(trustStore);
    ctxbuilder.setKeyStore(keyStore);
    ctxbuilder.setTracker(this::setPeerCertificateResult);

    ctx = ctxbuilder.build();
  }

  public void setPeerCertificateResult(X509Certificate[] chain, String authType, Throwable exception) {
    peerCertificateResult = new PeerCertificateDetails(chain, authType, exception);
  }

  public void tryssl(String hostname, int port) throws UnknownHostException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException, IOException {
    for (InetAddress address : this.resolver.resolve(hostname)) {
      tryssl(new InetSocketAddress(address, port), hostname);
    }
  }

  public SSLReportBuilder tryssl(InetSocketAddress address, String name) {
    return tryssl(address, name, 1000); // 1-second connect timeout
  } 
  
  public SSLReportBuilder tryssl(InetSocketAddress address, String name, int timeout) {
    SSLReportBuilder srb = new SSLReportBuilder();
    srb.setSSLContext(ctx);
    srb.setHostname(name);
    srb.setAddress(address);

    logger.debug("Trying {} (expected hostname {})", address, name);
    Socket socket = new Socket();
    try {
      logger.trace("Connecting to {}", address);
      socket.connect(address, timeout);
      logger.debug("Connection successful to {}", address);
    } catch (ConnectException e) {
      logger.debug("Connection failed to {}: {}", address, e);
      srb.setFailed(e);
      return srb;
    } catch (IOException e) {
      logger.error("Failed connecting to {}: {}", address, e);
      srb.setFailed(e);
      return srb;
    }

    tryssl(srb, socket, name);
    return srb;
  }

  public void tryssl(SSLReportBuilder srb, Socket socket, String name) {
    SSLSocketFactory socket_factory = ctx.getSocketFactory();
    
    SSLSocket ssl_socket;
    InetSocketAddress address = (InetSocketAddress)socket.getRemoteSocketAddress();

    try {
      ssl_socket = (SSLSocket)socket_factory.createSocket(socket, name, address.getPort(), true);
    } catch (IOException e) {
      srb.setFailed(e);
      return;
    }
    try {
      ssl_socket.startHandshake();
      logger.info("SSL Handshake successful to {}", address);
    } catch (SSLHandshakeException e) {
      srb.setFailed(new HandshakeProblem(e.getMessage(), ssl_socket.getHandshakeSession(), peerCertificateResult));
      Throwable cause = Blame.get(e);
      logger.warn("SSL Handshake failed: [{}] {}", cause.getClass(), cause.getMessage());
    } catch (IOException e) {
      logger.warn("Failed in SSL handshake to {}: {}", address, e);
      srb.setFailed(e);
    }

    return;
  }
}
