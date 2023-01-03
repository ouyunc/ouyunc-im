# OUYUNC-IM

#### 介绍
```
 (1)偶云客IM是一款基于netty的即时通讯框架，去中心化集群部署方案（简单解决脑裂问题）；
 (2)支持多协议传输（ws,wss,http,https）以及自定义协议，可自行扩展；
 (3)IM内置多种序列化方式如 jdk,json,hessian,hessian2,kryo,fst,thrift(暂未实现),protoStuff,protoBuf.并且客户端与服务端可以使用protoStuff和protoBuf进行相互转换；
 (4)支持文本消息，表情（emoji）,文件（图片/音视频/文档等）,语音（暂未开放）等功能；
 (5)内置db和cache做消息持久化(离线、历史)，高性能存储；
 (6)支持同一账号多设备在线（可控），消息漫游；
 (7)统一的编解码器，心跳检测；
 (8)多种算法数据加密；
 (9)SSL/TLS加密传输；
 (10)通过ack以及重试机制保证消息可靠，功能可扩展性很强；
 (11)tcp协议包packet                                                                                                             
  
|    1    |     1   |    1    |     8    |    1     |    1     |     4     |    1     |     1     |     1     |    4     |      n    |
+---------+---------+---------+----------+----------+----------+-----------+----------+-----------+-----------+----------+-----------+
|         |         |         |          |          |          |           |          |           |           |          |           |
|  魔数   |  协议类型| 协议版本 | 协议包id | 设备类型 | 网络类型  |  IP地址   | 加密算法 | 序列化算法 |  消息类型  | 消息长度 |  消息体    |
|         |         |         |          |          |          |           |          |           |           |          |           |
+---------+---------+---------+----------+----------+----------+-----------+----------+-----------+-----------+----------+-----------+



```
#### 3.x版本说明：
```
偶云客IM-v3.0.1 版本，更改包名由com.ouyu为com.couyunc；
重构集群代码相关逻辑，集群投递消息更加高效快捷；
增加多种业务消息处理器，可插拔和容易定制某个消息处理器，快速进行二次开发；
增加qos相关处理逻辑，保证消息可靠性传输；
默认消息处理器包括：登录，鉴权，私聊，群聊，已读消息，添加好友，删除好友，屏蔽好友，加群，踢出群，屏蔽群，解散群，拉黑(好友/群主拉黑某个客户端)，全量或按需拉取离线消息，历史消息，ack应答，广播，心跳等；

```
#### 软件架构
##### 开发环境：jdk1.8 + netty4.1.x

##### 软件架构图：
    
```

```
##### 代码目录结构：

```
![输入图片说明](docs/1672716976143.png)
```

##### 消息读写设计：
考虑到业务初期，群成员不是很多（小于500），私聊和群聊都采用写扩散的方式来进行读写操作，如果后期群成员很多，比如万人群，在进行优化使用混合模式来进行读写处理；

##### 存储设计
存储使用分布式缓存redis(通过插件的方式可以使用其他缓存实现) + mysql数据库（数据库可以启用/不启用，可以配置其他数据库如oracle,hbase等）

（I）redis 缓存key的设计如下：
    
```

```
（II）数据库表的设计如下：
  
```

```

 **注意** ：如果不做特别说明,全部以大端序读写《https://www.cnblogs.com/iathanasy/p/12617793.html》

#### 快速开始

```

```

#### 安装部署

```

```

#### 联系方式

   qq群:664955628

   基于该项目的一个即时通讯IM demo地址: https://m.ouyunc.com（服务器到期）
   
   注意：IM涉及到的视频会议由于服务器配置低以及带宽（2M）较小，会出现卡顿延迟现象

   如果感觉对你有帮助请点个star支持一下，感谢！

 
#### 下个版本
1. 支持客服相关业务
2. 支持弹幕相关业务
3. 支持语音等（已完成）
4. 支持语音聊天、视频聊天（已完成）
5. 在线简单视频会议（已完成）
6. 支持白板功能（已完成）
7. 集成springboot 以ouyunc-im-spring-boot-starter
8. 打成依赖包放到中央仓库，作为依赖组件来引用
9. 优化代码

#### 最后说明
由于本人技术有限，项目中可能会有bug或不代码规范的地方，如果对你带来了困扰请跳过本项目。
并且如果有任何想说的欢迎私信或提issue，咱们一起共同探讨交流。
另外如果商用请告知，否则带来的一些后果自负。

#### 参与贡献