package org.eu.qinjian;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.handler.HttpHandler;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.handler.RequestToBeSentAction;
import burp.api.montoya.http.handler.ResponseReceivedAction;
import javax.swing.JTextArea;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.xml.sax.InputSource;
import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BurpExtender implements BurpExtension, HttpHandler {
    private MontoyaApi api;
    private JPanel mainPanel;
    private JTextField runffField;
    private JTextField runnerbarField;
    private JTextArea logArea;
    private static final String TARGET_URL_PATTERN = "/html/live/s\\d+\\.html";    

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        
        // 设置插件名称
        api.extension().setName("维生素、爱运动 照片下载");
        
        // 注册 HTTP 处理器
        api.http().registerHttpHandler(this);
        
        // 创建 UI
        createUI();
        
    }

    private void createUI() {
        mainPanel = new JPanel(new BorderLayout());
        
        // 创建顶部面板
        JPanel topPanel = new JPanel(new FlowLayout());
        runffField = new JTextField("", 30);
        runnerbarField = new JTextField("",30);
        topPanel.add(new JLabel("维生素-监听号码牌:"));
        topPanel.add(runffField);
        topPanel.add(new JLabel("爱运动-监听号码牌:"));
        topPanel.add(runnerbarField);
        
        
        // 创建日志区域
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        
//        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        
        // 添加自定义标签页
        api.userInterface().registerSuiteTab("维生素、爱运动 照片下载", mainPanel);
    }

    private void createSaveDirectory(String pathName,String folderName) {
        try {
            Path path = Paths.get(pathName,folderName);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (Exception e) {
            log("创建目录失败: " + e.getMessage());
        }
    }

    @Override
	public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
		return null;
	}

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        String url = responseReceived.initiatingRequest().url();
        String requestBody = responseReceived.initiatingRequest().bodyToString();
        String method = responseReceived.initiatingRequest().method();

        // 从 URL 中提取 host
        String host = "";
        try {
            host = new URL(url).getHost();
        } catch (Exception e) {
            log("解析 URL 失败: " + e.getMessage());
        }
        
        // 处理维生素照片请求
        if (url.matches(".*" + TARGET_URL_PATTERN + ".*") && 
            requestBody.contains("<Action>getPhotoList</Action>") && 
            "www.runff.com".equals(host)) {
            try {
                // 使用 XML 解析器提取 number 值
                String number = "";
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setIgnoringElementContentWhitespace(true);
                DocumentBuilder builder = factory.newDocumentBuilder();
                
                // 处理请求体的编码
                byte[] bytes = requestBody.getBytes("ISO-8859-1");
                String decodedBody = new String(bytes, "UTF-8");
                
                InputSource is = new InputSource(new StringReader(decodedBody));
                is.setEncoding("UTF-8");
                org.w3c.dom.Document doc = builder.parse(is);
                org.w3c.dom.NodeList numberNodes = doc.getElementsByTagName("number");
                if (numberNodes.getLength() > 0) {
                    number = numberNodes.item(0).getTextContent();
                }
                log("提取到的number值: " + number);
                // 只有当 number 不为空且不等于"照片直播"时才处理
                if (!number.isEmpty() && !number.equals("照片直播")) {
                    String response = responseReceived.bodyToString();
                    createSaveDirectory("runff",number);
                    processXmlResponse_runff(response,number);//维生素
                }
            } catch (Exception e) {
                log("解析请求体失败: " + e.getMessage());
                log("错误详情: " + e.toString());
            }
        }

        // 处理爱运动照片请求
        if (url.contains("/yundong/faceSearch/getFaceAndGameNumSearchPhotoV2.json") && 
            "apiface.store.runnerbar.com".equals(host) &&
            "POST".equals(method)) {
            try {
                // 从 URL 中提取 game_number 参数
                String gameNumber = "";
                if (url.contains("game_number=")) {
                    gameNumber = url.substring(url.indexOf("game_number=") + "game_number=".length());
                    if (gameNumber.contains("&")) {
                        gameNumber = gameNumber.substring(0, gameNumber.indexOf("&"));
                    }
                }
                
                // 只有当 game_number 不为空时才处理
                if (!gameNumber.isEmpty()) {
                    String response = responseReceived.bodyToString();
                    createSaveDirectory("runnerbar",gameNumber);
                    processXmlResponse_runnerbar(response,gameNumber);//爱运动
                }
            } catch (Exception e) {
                log("处理响应失败: " + e.getMessage());
            }
        }
        return null;
    }
    
	
	public static String removeBom(String content) {
	    if (content != null && content.length() >= 3 && 
	        (int)content.charAt(0) == 0xEF && 
	        (int)content.charAt(1) == 0xBB && 
	        (int)content.charAt(2) == 0xBF) {
	        return content.substring(3);
	    }
	    return content;
	}
	

    private void processXmlResponse_runff(String response,String number) {
        try {
//            log("响应内容1: " + response);
            response = removeBom(response);
            log("维生素-响应内容: " + response);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputSource is = new InputSource(new StringReader(response));
            org.w3c.dom.Document doc = builder.parse(is);
            
            String listContent = doc.getElementsByTagName("list").item(0).getTextContent();
            Gson gson = new Gson();
            JsonArray jsonArray = gson.fromJson(listContent, JsonArray.class);
            log("维生素照片数量:"+jsonArray.size());
            for (int i = 0; i < jsonArray.size(); i++) {
                JsonObject photo = jsonArray.get(i).getAsJsonObject();
                String bigUrl = photo.get("big").getAsString();
                // 从 URL 中提取文件名
                String fileName = bigUrl.substring(bigUrl.lastIndexOf('/') + 1);
                String imageUrl = "http://p.chinarun.com" + bigUrl; // 需要替换为实际的域名
                log(imageUrl);
                downloadImage(imageUrl, "runff",number,fileName);
            }
            log("维生素照片下载完成 准备生成压缩包");
            createZipFile("runff",number);
            
        } catch (Exception e) {
        	log("解析 XML 失败: " + e.getMessage());
        	log("响应内容: " + response);
        }
    }
    

    private void processXmlResponse_runnerbar(String response,String number) {
        try {
            response = removeBom(response);
            log("爱运动-响应内容: " + response);
            
            // 解析 JSON 响应
            Gson gson = new Gson();
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            
            // 获取 result 对象
            JsonObject result = jsonResponse.getAsJsonObject("result");
            
            // 获取照片列表
            JsonArray photos = result.getAsJsonArray("topicInfoList");
            log("爱运动照片数量:" + photos.size());
            
            for (int i = 0; i < photos.size(); i++) {
                JsonObject photo = photos.get(i).getAsJsonObject();
                String photoUrl = photo.get("url_hq").getAsString();
                String fileName = photoUrl.substring(photoUrl.lastIndexOf('/') + 1);
                log(photoUrl);
                downloadImage(photoUrl, "runnerbar",number, fileName);
            }
            
            log("爱运动照片下载完成 准备生成压缩包");
            createZipFile("runnerbar",number);
            
        } catch (Exception e) {
            log("解析 JSON 失败: " + e.getMessage());
            log("响应内容: " + response);
        }
    }

    private void downloadImage(String imageUrl, String pathName,String folderName,String fileName) {
        try {
            URL url = URI.create(imageUrl).toURL();
            Path savePath = Paths.get(pathName,folderName, fileName);
            
            try (java.io.InputStream in = url.openStream()) {
                Files.copy(in, savePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                log("成功下载图片: " + fileName);
            }
        } catch (Exception e) {
            log("下载图片失败: " + fileName + " - " + e.getMessage());
        }
    }

    private void createZipFile(String pathName,String folderName) {
        try {
            Path sourceFolderPath = Paths.get(pathName,folderName);
            Path zipFilePath = Paths.get(pathName, folderName + ".zip");
            
            // 创建 ZIP 文件
            try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                    Files.newOutputStream(zipFilePath))) {
                
                // 遍历文件夹中的所有文件
                Files.walk(sourceFolderPath)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        try {
                            // 计算相对路径
                            String relativePath = sourceFolderPath.relativize(path).toString();
                            
                            // 创建 ZIP 条目
                            java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(relativePath);
                            zos.putNextEntry(zipEntry);
                            
                            // 写入文件内容
                            Files.copy(path, zos);
                            zos.closeEntry();
                            
                            log("添加文件到压缩包: " + relativePath);
                        } catch (Exception e) {
                            log("压缩文件失败: " + path + " - " + e.getMessage());
                        }
                    });
            }
            
            log("压缩包创建完成: " + zipFilePath);
            
        } catch (Exception e) {
            log("创建压缩包失败: " + e.getMessage());
        }
    }

    private void log(String message) {
        logArea.append(message + "\n");
    }
    
    public static void main(String[] args){


    }

	
} 