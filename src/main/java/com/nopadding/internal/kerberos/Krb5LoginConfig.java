package com.nopadding.internal.kerberos;

import com.sun.security.auth.module.Krb5LoginModule;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;

public final class Krb5LoginConfig extends Configuration {

  private static final String SUN_KRB5_LOGIN_MODULE_CLASS_NAME =
      Krb5LoginModule.class.getCanonicalName();

  private final AppConfigurationEntry[] appConfigurationEntries;

  /**
   * Get options for username and password login.
   *
   * @return config options
   */
  public static Krb5LoginConfig getUsernameAndPasswordInstance() {
    Map<String, String> options = new HashMap<>();
    options.put("debug", "false");
    options.put("storeKey", "true");
    return new Krb5LoginConfig(options);
  }

  /**
   * Get options for ticket cache login.
   *
   * @param principal principal used to get ticket
   * @return config options
   */
  public static Krb5LoginConfig getTicketCacheInstance(String principal) {
    Map<String, String> options = new HashMap<>();
    options.put("debug", "false");
    options.put("renewTGT", "true");
    options.put("principal", principal);
    options.put("useTicketCache", "true");
    options.put("doNotPrompt", "true");
    return new Krb5LoginConfig(options);
  }

  /**
   * Get options for keytab login.
   *
   * @param principal principal used to login
   * @param keyTab keytab used to login
   * @return config options
   */
  public static Krb5LoginConfig getKeyTabInstance(String principal, File keyTab) {
    Map<String, String> options = new HashMap<>();
    options.put("debug", "false");
    options.put("principal", principal);
    options.put("useKeyTab", "true");
    options.put("keyTab", keyTab.getPath());
    options.put("storeKey", "true");
    options.put("doNotPrompt", "true");
    return new Krb5LoginConfig(options);
  }

  Krb5LoginConfig(Map<String, String> options) {
    this.appConfigurationEntries = new AppConfigurationEntry[]{
        new AppConfigurationEntry(
            SUN_KRB5_LOGIN_MODULE_CLASS_NAME,
            AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
            options
        )
    };
  }

  @Override
  public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
    return appConfigurationEntries;
  }
}
