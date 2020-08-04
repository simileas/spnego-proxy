package com.nopadding.internal.kerberos;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class SpnegoProvider {

  /**
   * Used by the BASIC Auth mechanism for establishing a LoginContext
   * to authenticate a client/caller/request.
   *
   * @param username client username
   * @param password client password
   * @return CallbackHandler to be used for establishing a LoginContext
   */
  static CallbackHandler getUsernameAndPasswordHandler(
      final String username, final String password) {
    log.debug("username=" + username + "; password=" + password.hashCode());
    return new CallbackHandler() {
      @Override
      public void handle(Callback[] callbacks) {
        for (Callback callback : callbacks) {
          if (callback instanceof NameCallback) {
            final NameCallback nameCallback = (NameCallback) callback;
            nameCallback.setName(username);
          } else if (callback instanceof PasswordCallback) {
            final PasswordCallback passCallback = (PasswordCallback) callback;
            passCallback.setPassword(password.toCharArray());
          } else {
            log.warn("Unsupported Callback class=" + callback.getClass().getName());
          }
        }
      }
    };
  }
}
