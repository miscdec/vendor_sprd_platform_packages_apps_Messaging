#!/bin/bash

# 转换平台签名命令
# signs_release.jks : 签名文件
# android : 签名文件密码
# platform.pk8、platform.x509.pem : 系统签名文件
# platform : 签名文件别名
./keytool-importkeypair -k signs_release.jks -p 123456 -pk8 platform.pk8 -cert platform.x509.pem -alias platform