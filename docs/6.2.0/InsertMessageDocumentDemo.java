import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.Date;

public class InsertMessageDocument {
    public static void main(String[] args) {
        // 连接 MongoDB
        MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017");
        // 获取数据库
        MongoDatabase database = mongoClient.getDatabase("yourDatabaseName");
        // 获取集合
        MongoCollection<Document> collection = database.getCollection("ouyunc_im_message");

        // 设置文档的过期时间为当前时间后的 60 秒
        Date expireDate = new Date(System.currentTimeMillis() + 60 * 1000);
        // 创建文档
        Document doc = new Document("_id", 1234567890L)
               .append("protocol", 1)
               .append("protocol_version", 1)
               .append("device_type", 2)
               .append("network_type", 3)
               .append("encrypt_type", 1)
               .append("serialize_algorithm", 2)
               .append("message_type", 4)
               .append("retain", 0)
               .append("client_ip", "192.168.1.100")
               .append("from", "user123")
               .append("to", "group456")
               .append("content_type", 1)
               .append("content", "这是一条测试消息")
               .append("qos", 1)
               .append("at", new ArrayList<>("user123","user456","user789"))
               .append("extra", "一些额外的消息扩展内容")
               .append("client_send_time", System.currentTimeMillis())
               .append("server_arrival_time", System.currentTimeMillis())
               .append("read", false)
               .append("withdrawn", false)
                .append("create_time", new Date())
                .append("update_time", new Date())
               .append("expire_at", expireDate);


        // 插入文档
        collection.insertOne(doc);

        // 关闭连接
        mongoClient.close();
    }
}