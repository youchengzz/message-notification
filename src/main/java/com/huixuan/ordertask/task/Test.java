package com.huixuan.ordertask.task;

import com.huixuan.ordertask.config.SignUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * 功能描述
 *
 * @author: wpeng
 * @date: 2026年04月02日 10:43
 */
public class Test {


    /**
     * 查询用户余额
     *
     * @param platformDomain 平台域名
     * @param userId         用户ID(AppId)
     * @param apiKey         API密钥
     * @return 包含余额信息的Map
     */
    public static Map<String, Object> queryUserBalance(String platformDomain, String userId, String appKey) {
        try {
            // 构建请求URL
            URL url = new URL(platformDomain + "/api/v1/user/info");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // 设置请求方法
            connection.setRequestMethod("POST");

            // 生成13位时间戳
            String timestamp = String.valueOf(System.currentTimeMillis());

            // 构建待签名字符串 (timestamp + body + apiKey)
            String body = "{}";
            String signString = timestamp + body + appKey;

            // 设置请求头
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Sign", SignUtil.sha1Encrypt(signString));
            connection.setRequestProperty("Timestamp", timestamp);
            connection.setRequestProperty("UserId", userId);

            // 启用输出流
            connection.setDoOutput(true);

            // 写入请求体
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = body.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // 获取响应码
            int responseCode = connection.getResponseCode();

            // 读取响应
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "utf-8"))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
            }

            // 解析JSON响应（这里简化处理，实际项目建议使用Jackson或Gson库）
            Map<String, Object> result = parseJsonResponse(response.toString());

            // 添加额外信息
            result.put("request_timestamp", timestamp);
            result.put("sign_string", signString);
            result.put("response_code", responseCode);

            return result;

        } catch (Exception e) {
            System.err.println("请求失败: " + e.getMessage());
            e.printStackTrace();

            // 返回错误信息
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", true);
            errorResult.put("message", e.getMessage());
            return errorResult;
        }
    }

    /**
     * 简化的JSON解析方法（实际项目建议使用Jackson或Gson库）
     */
    private static Map<String, Object> parseJsonResponse(String jsonResponse) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 移除首尾空白字符
            jsonResponse = jsonResponse.trim();

            // 去掉最外层的大括号
            if (jsonResponse.startsWith("{") && jsonResponse.endsWith("}")) {
                jsonResponse = jsonResponse.substring(1, jsonResponse.length() - 1);
            }

            // 分割键值对
            String[] pairs = jsonResponse.split(",(?=\\s*[\"{])");

            for (String pair : pairs) {
                pair = pair.trim();

                // 查找冒号的位置
                int colonIndex = findColonIndex(pair);
                if (colonIndex == -1) continue;

                String key = pair.substring(0, colonIndex).trim();
                String value = pair.substring(colonIndex + 1).trim();

                // 清理键名
                key = cleanJsonKey(key);

                // 清理值
                value = cleanJsonValue(value);

                // 特殊处理嵌套对象
                if (value.startsWith("{") && value.endsWith("}")) {
                    // 这里简化处理data字段
                    if ("data".equals(key)) {
                        Map<String, Object> data = parseDataObject(value);
                        result.put(key, data);
                    } else {
                        result.put(key, value);
                    }
                } else {
                    // 尝试转换为数字类型
                    if (isNumeric(value)) {
                        result.put(key, Double.parseDouble(value));
                    } else {
                        result.put(key, value);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("JSON解析失败: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    /**
     * 查找不在引号内的冒号位置
     */
    private static int findColonIndex(String str) {
        boolean inQuotes = false;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '"' && (i == 0 || str.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
            } else if (c == ':' && !inQuotes) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 清理JSON键名
     */
    private static String cleanJsonKey(String key) {
        key = key.trim();
        if (key.startsWith("\"") && key.endsWith("\"")) {
            key = key.substring(1, key.length() - 1);
        }
        return key;
    }

    /**
     * 清理JSON值
     */
    private static String cleanJsonValue(String value) {
        value = value.trim();
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    /**
     * 解析data对象
     */
    private static Map<String, Object> parseDataObject(String dataStr) {
        Map<String, Object> data = new HashMap<>();

        // 移除首尾大括号
        dataStr = dataStr.trim();
        if (dataStr.startsWith("{") && dataStr.endsWith("}")) {
            dataStr = dataStr.substring(1, dataStr.length() - 1);
        }

        // 查找balance字段
        if (dataStr.contains("\"balance\"")) {
            String[] parts = dataStr.split(",");
            for (String part : parts) {
                part = part.trim();
                if (part.contains("\"balance\"")) {
                    int colonIndex = part.indexOf(':');
                    if (colonIndex != -1) {
                        String balanceValue = part.substring(colonIndex + 1).trim();
                        balanceValue = cleanJsonValue(balanceValue);
                        data.put("balance", balanceValue);
                    }
                }
            }
        }

        return data;
    }

    /**
     * 判断字符串是否为数字
     */
    private static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 测试方法
     */
    public static void main(String[] args) {
        // 示例调用
        String platformDomain = "https://shop.cardvip.cc"; // 替换为实际平台域名
        String userId = "gp2OPiuec4Ifbw0x1hrJ6GM5dFqDnEys";
        String appKey = "FHqCjw8X94YzdVMTYuQlqmi5UJVGp7mXTNY7UsphWXTzKh"; // 替换为实际用户ID

        System.out.println("开始查询用户余额...");
        Map<String, Object> result = queryUserBalance(platformDomain, userId, appKey);

        System.out.println("查询结果:");
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
    }

}
