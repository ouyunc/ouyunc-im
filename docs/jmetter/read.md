### 注意：压测之前请先修改登录的部分片段代码，目的是为了模拟真实环境。由于websocket 压测使用了 第三方插件，由于插件的问题请不在在一台window电脑上开启超过1万的线程组，可以拆分多台电脑来测试
代码片段修改如下：
```
    String identity = loginContent.getIdentity();
    Long s = Long.valueOf(identity) + SnowflakeUtil.nextId();
    loginContent.setIdentity(s.toString());
    loginMessage.setContent(JSONUtil.toJsonStr(loginContent));
```

![img.png](img.png)
