# Spnego Proxy

![os](https://img.shields.io/badge/Os-Linux-yellow)
![usage](https://img.shields.io/badge/Usage-Proxy%20Tool-red) 
![然](https://img.shields.io/badge/%E4%BA%A4%E4%B8%AA-%E6%9C%8B%E5%8F%8B-blue)

## Motivation

对 Hadoop 集群中的，访问启用 Kerberos 认证的 web 页面时，需要在客户端配置 kerberos 环境。

这是一个比较麻烦的过程，尤其是在 Windows 开发环境下。

所以想用 HTTP 代理的方式解决访问 web 页面的问题。

jaas 文件示例:

```text
com.sun.security.jgss.initiate {
  com.sun.security.auth.module.Krb5LoginModule required
  debug=false
  useTicketCache=true
  storeKey=false
  doNotPrompt=true
  renewTGT=true
  useKeyTab=true
  isInitiator=true
  keyTab="/home/admin/admin.keytab"
  principal="admin";
};
```

## Build

````shell script
./gradlew distTar -info
````

## TODO

- Configuration injector
- 复用 outbound channel
- 只对返回  **`401 Unauthorized`** 的请求加入 token
- HTTPS
- 更简洁的 Kerberos 配置方式
