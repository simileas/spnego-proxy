package com.nopadding.internal.kerberos;

import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Base64;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import lombok.extern.slf4j.Slf4j;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

@Slf4j
public final class SpnegoContext {

  /**
   * Get token.
   *
   * @param username kerberos username
   * @param password kerberos password
   * @param server spnego http server host
   * @return base64 encoded token.
   * @throws LoginException failed to initialize gss mechanism
   * @throws GSSException failed to initialize gss context
   * @throws PrivilegedActionException failed to login
   */
  public static String getToken(String username, String password, String server)
      throws LoginException, GSSException, PrivilegedActionException {
    LoginContext lc = Krb5LoginContext.getInstance(username, password);
    lc.login();
    Subject subject = lc.getSubject();
    Oid krb5Mechanism = new Oid("1.2.840.113554.1.2.2");
    Oid krb5PrincipalNameType = new Oid("1.2.840.113554.1.2.2.1");
    GSSManager manager = GSSManager.getInstance();
    GSSName serverName = manager
        .createName("HTTP/" + server, krb5PrincipalNameType);
    GSSContext context =
        manager.createContext(serverName, krb5Mechanism, null, GSSContext.DEFAULT_LIFETIME);

    // set desired context options prior to context establishment
    context.requestConf(true);
    context.requestMutualAuth(true);
    context.requestReplayDet(true);
    context.requestSequenceDet(true);
    byte[] clientToken = Subject.doAs(subject, new PrivilegedExceptionAction<byte[]>() {
      @Override
      public byte[] run() throws GSSException {
        return context.initSecContext(new byte[0], 0, 0);
      }
    });
    context.dispose();
    lc.logout();
    return Base64.getEncoder().encodeToString(clientToken);
  }
}
