package se.bjurr.violations.comments.gitlab.lib.client;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.logging.Level.INFO;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.logging.Level;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509ExtendedTrustManager;
import se.bjurr.violations.comments.gitlab.lib.TokenType;
import se.bjurr.violations.lib.ViolationsLogger;

public class GitLabInvoker {
  private static final String MASK = "HIDDEN";

  public enum Method {
    DELETE,
    GET,
    POST,
    PUT
  }

  private final HttpClient httpClient;
  private final TokenType tokenType;
  private final String apiToken;
  private final String proxyPassword;
  private final boolean logRequestResponse;

  public GitLabInvoker(
      final TokenType tokenType,
      final String apiToken,
      final boolean ignoreCertificateErrors,
      final String proxyServer,
      final String proxyUser,
      final String proxyPassword,
      final boolean logRequestResponse) {
    this.tokenType = tokenType;
    this.apiToken = apiToken;
    this.proxyPassword = proxyPassword;
    this.logRequestResponse = logRequestResponse;
    this.httpClient =
        buildHttpClient(ignoreCertificateErrors, proxyServer, proxyUser, proxyPassword);
  }

  private static HttpClient buildHttpClient(
      final boolean ignoreCertificateErrors,
      final String proxyServer,
      final String proxyUser,
      final String proxyPassword) {
    final HttpClient.Builder builder =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30));
    if (ignoreCertificateErrors) {
      builder.sslContext(trustAllSslContext());
    }
    if (proxyServer != null) {
      final URI proxyUri = URI.create(proxyServer);
      final int port = proxyUri.getPort() > 0 ? proxyUri.getPort() : 80;
      builder.proxy(ProxySelector.of(new InetSocketAddress(proxyUri.getHost(), port)));
      if (proxyUser != null && proxyPassword != null) {
        builder.authenticator(
            new Authenticator() {
              @Override
              protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(proxyUser, proxyPassword.toCharArray());
              }
            });
      }
    }
    return builder.build();
  }

  /**
   * Only built when the caller opted in via {@code ignoreCertificateErrors} - the same escape hatch
   * gitlab4j-api's {@code setIgnoreCertificateErrors} offered.
   */
  @SuppressFBWarnings("WEAK_TRUST_MANAGER")
  private static final class TrustAllTrustManager extends X509ExtendedTrustManager {
    @Override
    public void checkClientTrusted(final X509Certificate[] chain, final String authType) {}

    @Override
    public void checkServerTrusted(final X509Certificate[] chain, final String authType) {}

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }

    @Override
    public void checkClientTrusted(
        final X509Certificate[] chain, final String authType, final java.net.Socket socket) {}

    @Override
    public void checkServerTrusted(
        final X509Certificate[] chain, final String authType, final java.net.Socket socket) {}

    @Override
    public void checkClientTrusted(
        final X509Certificate[] chain,
        final String authType,
        final javax.net.ssl.SSLEngine engine) {}

    @Override
    public void checkServerTrusted(
        final X509Certificate[] chain,
        final String authType,
        final javax.net.ssl.SSLEngine engine) {}
  }

  private static SSLContext trustAllSslContext() {
    try {
      final SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(
          null, new javax.net.ssl.TrustManager[] {new TrustAllTrustManager()}, new SecureRandom());
      return sslContext;
    } catch (final Exception e) {
      throw new RuntimeException("Could not build a trust-all SSLContext", e);
    }
  }

  private String tokenHeaderName() {
    switch (this.tokenType) {
      case PRIVATE:
        return "PRIVATE-TOKEN";
      case JOB_TOKEN:
        return "JOB-TOKEN";
      case ACCESS:
      case OAUTH2_ACCESS:
      default:
        return "Authorization";
    }
  }

  private String tokenHeaderValue() {
    switch (this.tokenType) {
      case PRIVATE:
      case JOB_TOKEN:
        return this.apiToken;
      case ACCESS:
      case OAUTH2_ACCESS:
      default:
        return "Bearer " + this.apiToken;
    }
  }

  public GitLabResponse invoke(
      final ViolationsLogger violationsLogger,
      final String url,
      final Method method,
      final String jsonBody) {
    try {
      final HttpRequest.Builder requestBuilder =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(30))
              .header(this.tokenHeaderName(), this.tokenHeaderValue())
              .header("Accept", "application/json")
              .header("Content-Type", "application/json; charset=utf-8");

      switch (method) {
        case DELETE:
          requestBuilder.DELETE();
          break;
        case GET:
          requestBuilder.GET();
          break;
        case POST:
          requestBuilder.POST(
              jsonBody == null
                  ? BodyPublishers.noBody()
                  : BodyPublishers.ofString(jsonBody, UTF_8));
          break;
        case PUT:
          requestBuilder.PUT(
              jsonBody == null
                  ? BodyPublishers.noBody()
                  : BodyPublishers.ofString(jsonBody, UTF_8));
          break;
        default:
          throw new IllegalArgumentException("Unsupported http method: " + method);
      }

      final HttpResponse<String> response =
          this.httpClient.send(requestBuilder.build(), BodyHandlers.ofString(UTF_8));
      final int statusCode = response.statusCode();
      final String body = response.body();

      if (this.logRequestResponse) {
        final boolean wasNotOk = statusCode < 200 || statusCode >= 300;
        final Level level = wasNotOk ? Level.WARNING : INFO;
        violationsLogger.log(
            level,
            this.mask(
                method
                    + " "
                    + url
                    + " "
                    + statusCode
                    + "\nSent:\n"
                    + jsonBody
                    + "\nResponse:\n"
                    + body));
      }

      return new GitLabResponse(statusCode, body);
    } catch (final Exception e) {
      throw new RuntimeException("Error calling:\n" + url + "\n" + method + "\n" + jsonBody, e);
    }
  }

  private String mask(String message) {
    message = message.replace(this.apiToken, MASK);
    if (this.proxyPassword != null) {
      message = message.replace(this.proxyPassword, MASK);
    }
    return message;
  }
}
