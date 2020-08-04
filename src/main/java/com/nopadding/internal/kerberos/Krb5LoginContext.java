package com.nopadding.internal.kerberos;

import java.io.File;
import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

public final class Krb5LoginContext extends LoginContext {

  private static final String UNUSED_CONFIGURATION_NAME = "";

  Krb5LoginContext(
      String name, Subject subject, CallbackHandler callbackHandler, Configuration config)
      throws LoginException {
    super(name, subject, callbackHandler, config);
  }

  /**
   * 使用用户名和密码登录.
   *
   * @param username 用户名
   * @param password 密码
   * @return LoginContext to be used for login
   * @throws LoginException 登录异常
   */
  public static Krb5LoginContext getInstance(String username, String password)
      throws LoginException {
    return new Krb5LoginContext(UNUSED_CONFIGURATION_NAME,
        null,
        SpnegoProvider.getUsernameAndPasswordHandler(username, password),
        Krb5LoginConfig.getUsernameAndPasswordInstance()
    );
  }

  /**
   * 使用 principle 和 keytab 登录.
   *
   * @param principal principal
   * @param keyTab    keytab 文件
   * @return LoginContext to be used for login
   * @throws LoginException 登录异常
   */
  public static Krb5LoginContext getInstance(String principal, File keyTab)
      throws LoginException {
    return new Krb5LoginContext(UNUSED_CONFIGURATION_NAME,
        null,
        null,
        Krb5LoginConfig.getKeyTabInstance(principal, keyTab)
    );
  }

  /**
   * 使用 ticket cache 登录.
   *
   * @param principal principal
   * @return LoginContext to be used for login
   * @throws LoginException 登录异常
   */
  public static Krb5LoginContext getInstance(String principal) throws LoginException {
    return new Krb5LoginContext(UNUSED_CONFIGURATION_NAME,
        null,
        null,
        Krb5LoginConfig.getTicketCacheInstance(principal)
    );
  }
}
