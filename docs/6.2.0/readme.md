该[Message.proto](Message.proto)文件，是proto原始文件，可通过工具和该文件生成多种语言的文件来使用；
ouyunc-protobuf-message.js  文件就是使用工具和该文件生成的js 文件；


使用步骤：
文件ouyunc-protobuf-message.js 不是必须的;
如果不使用protobuf序列化（使用json序列化），不需要引入这个文件；只需引入[ouyunc-message-sdk.js](ouyunc-message-sdk.js)即可，可参考socket.html