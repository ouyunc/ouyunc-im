import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.Date;

public class InsertReadReceiptDocument {
    public static void main(String[] args) {
        // 连接 MongoDB
        MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017");
        // 获取数据库
        MongoDatabase database = mongoClient.getDatabase("yourDatabaseName");
        // 获取集合
        MongoCollection<Document> collection = database.getCollection("ouyunc_im_read_receipt");

        // 创建文档
        Document doc = new Document("_id", 123456789L)
               .append("msg_id", 987654321L)
               .append("user_id", "user_001")
               .append("read_time", System.currentTimeMillis())
               .append("create_time", new Date());

        // 插入文档
        collection.insertOne(doc);

        // 关闭连接
        mongoClient.close();
    }
}