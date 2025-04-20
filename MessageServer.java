import static spark.Spark.*;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class MessageServer {
    static final String FILE_PATH = "messages.txt";
    static Gson gson = new Gson();

    public static void main(String[] args) {
        port(8080);
        enableCORS();

        // POST /messages - save message
        post("/messages", (req, res) -> {
            res.type("application/json");
            Map body = gson.fromJson(req.body(), Map.class);
            String message = (String) body.get("message");
            Files.write(Paths.get(FILE_PATH), (message + "\n").getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return gson.toJson(Map.of("status", "saved"));
        });

        // GET /messages - read all messages
        get("/messages", (req, res) -> {
            res.type("application/json");
            if (!Files.exists(Paths.get(FILE_PATH))) return "[]";
            List<String> messages = Files.readAllLines(Paths.get(FILE_PATH));
            return gson.toJson(messages);
        });
    }

    private static void enableCORS() {
        options("/*", (req, res) -> {
            String accessControlRequestHeaders = req.headers("Access-Control-Request-Headers");
            if (accessControlRequestHeaders != null) {
                res.header("Access-Control-Allow-Headers", accessControlRequestHeaders);
            }

            String accessControlRequestMethod = req.headers("Access-Control-Request-Method");
            if (accessControlRequestMethod != null) {
                res.header("Access-Control-Allow-Methods", accessControlRequestMethod);
            }

            return "OK";
        });

        before((req, res) -> res.header("Access-Control-Allow-Origin", "*"));
    }
}
