# Spnego Proxy

![os](https://img.shields.io/badge/Os-Linux-yellow)
![usage](https://img.shields.io/badge/Usage-Proxy%20Tool-red) 
![然](https://img.shields.io/badge/%E4%BA%A4%E4%B8%AA-%E6%9C%8B%E5%8F%8B-blue)

## Motivation

对 Hadoop 集群中的，访问启用 Kerberos 认证的 web 页面时，需要在客户端配置 kerberos 环境。

这是一个比较麻烦的过程，尤其是在 Windows 开发环境下。

所以想用 HTTP 代理的方式解决访问 web 页面的问题。

配置文件

````properties
# config items

# 本地
sp.bind.address = 0.0.0.0
sp.port = 8100

# 拦截请求注入 header 的 domain，如果请求的地址是 *.in.nopadding.com，会被注入 token。
sp.domain = in.nopadding.com

# kerberos 用户名和密码
sp.username = admin
sp.password = pa$$word

# KDC realm
java.security.krb5.realm = IN.NOPADDING.COM

# krb5kdc 地址
java.security.krb5.kdc = ux4.in.nopadding.com
````

## Build

代码样式检查：

````shell script
./gradlew check
````

生成发布版本：

````shell script
./gradlew distTar -info
````

## TODO

- [ ] Configuration injector
- [x] 复用 outbound channel
- [ ] 只对返回  **`401 Unauthorized`** 的请求加入 token
- [ ] 完善 HTTPS 请求的处理
- [x] 更简洁的 Kerberos 配置方式
