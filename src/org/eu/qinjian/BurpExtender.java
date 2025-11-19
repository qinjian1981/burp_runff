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
import com.google.gson.JsonElement;
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

import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.element.Paragraph;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import com.itextpdf.kernel.colors.DeviceGray;
import javax.swing.SpinnerDateModel;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.util.Date;

public class BurpExtender implements BurpExtension, HttpHandler {
    private MontoyaApi api;
    private JPanel mainPanel;
    private JTextField runffField;
    private JTextField runnerbarField;
    private JTextField photoplusField;
    private JTextArea logArea;
//    private JSpinner dateSpinner;
    private static final String TARGET_URL_PATTERN = "/html/live/s\\d+\\.html";    

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        
        // 设置插件名称
        api.extension().setName("维生素、爱运动、跑野时刻 照片下载 极课错题生成");
        
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
        photoplusField = new JTextField("",30);
        
        // 创建日期选择器
//        SpinnerDateModel dateModel = new SpinnerDateModel();
//        dateSpinner = new JSpinner(dateModel);
//        dateSpinner.setEditor(new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd"));
        
        // 设置默认日期为3个月前
//        Calendar calendar = Calendar.getInstance();
//        calendar.add(Calendar.MONTH, -3);
//        dateSpinner.setValue(calendar.getTime());
        
        topPanel.add(new JLabel("维生素-监听号码牌:"));
        topPanel.add(runffField);
        topPanel.add(new JLabel("爱运动-监听号码牌:"));
        topPanel.add(runnerbarField);
        topPanel.add(new JLabel("跑野时刻-监听号码牌:"));
        topPanel.add(photoplusField);
//        topPanel.add(new JLabel("错题日期:"));
//        topPanel.add(dateSpinner);
        
        // 创建日志区域
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        
        // 添加自定义标签页
        api.userInterface().registerSuiteTab("维生素、爱运动 照片下载 极课错题生成", mainPanel);
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
        
        
        // 处理跑野时刻请求
        if (url.contains("/home/pic/self/recognize") && 
            "live.photoplus.cn".equals(host) &&
            "GET".equals(method)) {
            try {
                // 从 URL 中提取 number 参数
                String Number = "";
                if (url.contains("number=")) {
                	Number = url.substring(url.indexOf("number=") + "number=".length());
                    if (Number.contains("&")) {
                    	Number = Number.substring(0, Number.indexOf("&"));
                    }
                }
                
                // 只有当 number 不为空时才处理
                if (!Number.isEmpty()) {
                    String response = responseReceived.bodyToString();
                    createSaveDirectory("photoplus",Number);
                    processJsonResponse_photoplus(response,Number);//跑野时刻
                }
            } catch (Exception e) {
                log("处理响应失败: " + e.getMessage());
            }
        }
        
        // 处理极课错题
//        if (url.contains("/ark/home/api/get_wrong_question_group_list") && 
//            "parent.fclassroom.com".equals(host) &&
//            "POST".equals(method)) {
//            try {
//                // 从 URL 中提取 aid 参数
//                String aid = "";
//                if (url.contains("aid=")) {
//                    aid = url.substring(url.indexOf("aid=") + "aid=".length());
//                    if (aid.contains("&")) {
//                        aid = aid.substring(0, aid.indexOf("&"));
//                    }
//                }
//                
//                // 只有当 aid 不为空时才处理
//                if (!aid.isEmpty()) {
//                    // 获取响应内容并确保使用UTF-8编码
//                    byte[] responseBytes = responseReceived.body().getBytes();
//                    String response = new String(responseBytes, "UTF-8");
////                    log(response);
//                    createSaveDirectory("fclassroom",aid);
//                    processJsonResponse_fclassroom(response,aid);//极课错题
//                }
//            } catch (Exception e) {
//                log("处理响应失败: " + e.getMessage());
//            }
//        }
        
        
        return null;
    }
    
	
//	private void processJsonResponse_fclassroom(String response, String aid) {
//		try {
//			log("极课错题-响应内容: " + response);
//			
//			Gson gson = new Gson();
//			JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
//			
//			if (jsonResponse.get("code").getAsInt() == 200) {
//				JsonObject data = jsonResponse.getAsJsonObject("data");
//				JsonArray wrongQuestionGroups = data.getAsJsonArray("wrong_question_group_list");
//				log("找到错题组数量: " + wrongQuestionGroups.size());
//				
//				// 获取用户选择的日期
//				java.util.Date selectedDate = (java.util.Date) dateSpinner.getValue();
//				long selectedTimestamp = selectedDate.getTime();
//				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//				log("选择的日期: " + sdf.format(selectedDate));
//				
//				// 创建PDF文档
//				String pdfPath = "fclassroom/" + aid + "/错题集.pdf";
//				log("开始创建PDF文件: " + pdfPath);
//				
//				// 创建PDF文档
//				PdfWriter writer = new PdfWriter(pdfPath);
//				PdfDocument pdfDoc = new PdfDocument(writer);
//				Document document = new Document(pdfDoc);
//				
//				// 设置字体
//				DefaultFontProvider fontProvider = new DefaultFontProvider();
//				fontProvider.addFont("fonts/simsun.ttf");
//				ConverterProperties properties = new ConverterProperties();
//				properties.setFontProvider(fontProvider);
//				
//				// 创建页脚字体
//				PdfFont footerFont = PdfFontFactory.createFont("fonts/simsun.ttf", "Identity-H", pdfDoc);
//				// 添加页脚处理器
//				pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, new PageFooterHandler(footerFont));
//				
//				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
//				String currentDate = LocalDate.now().format(formatter);
//			
//				
//				// 遍历错题组
//				for (JsonElement groupElement : wrongQuestionGroups) {
//					JsonObject group = groupElement.getAsJsonObject();
//					String examName = group.get("exam_name").getAsString();
//					long publishTime = group.get("publish_time").getAsLong();
//					String publishTimeStr = sdf.format(new Date(publishTime));
//					
//					// 只处理选择日期之后的错题
//					if (publishTime >= selectedTimestamp) {
//						log("处理考试: " + examName + " (发布时间: " + publishTimeStr + ")");
//						
//						// 添加考试名称
//						String titleHtml = "<div style='text-align: center; font-size: 16pt; font-weight: bold; margin-bottom: 20px;'>" + examName + "</div>";
//						
//						// 处理题目列表
//						JsonArray questionList = group.getAsJsonArray("question_list");
//						log("该考试错题数量: " + questionList.size());
//						
//						// 构建完整的 HTML 内容
//						StringBuilder fullHtml = new StringBuilder();
//						fullHtml.append(titleHtml);
//						
//						for (JsonElement questionElement : questionList) {
//							JsonObject question = questionElement.getAsJsonObject();
//							String questionNo = question.get("paper_q_no").getAsString();
//							String contentText = question.get("content_text").getAsString();
//							int questionType = question.get("question_type").getAsInt();
//							log("处理题目: " + questionNo);
//							log("题目内容: " + contentText);
//							log("题目类型: " + questionType);
//							
//							// 根据题目类型设置不同的样式和预留空间
//							String questionStyle = "";
//							String answerSpace = "";
//							String questionTypeStr = "";
//							
//							switch (questionType) {
//								case 1: // 单选题
//									questionStyle = "font-size: 12pt; margin-bottom: 15px;";
//									answerSpace = "<div style='height: 5px;'></div>"; // 单选题预留较小空间
//									questionTypeStr = "单选题";
//									break;
//								case 2: // 填空题
//									questionStyle = "font-size: 12pt; margin-bottom: 15px;";
//									answerSpace = "<div style='height: 5px;'></div>"; // 填空题预留中等空间
//									questionTypeStr = "填空题";
//									break;
//								case 3: // 解答题
//									questionStyle = "font-size: 12pt; margin-bottom: 15px;";
//									answerSpace = "<div style='height: 150px;'></div>"; // 解答题预留较大空间
//									questionTypeStr = "解答题";
//									break;
//								case 8: // 混合题
//									questionStyle = "font-size: 12pt; margin-bottom: 15px;";
//									answerSpace = "<div style='height: 150px;'></div>"; // 混合题预留较大空间
//									questionTypeStr = "混合题";
//									break;
//								default:
//									questionStyle = "font-size: 12pt; margin-bottom: 15px;";
//									answerSpace = "<div style='height: 20px;'></div>";
//									questionTypeStr = "未知题型";
//							}
//							
//							// 将题目内容添加到 HTML，使用表格形式展示
//							fullHtml.append("<table style='width: 100%; border-collapse: collapse; margin-bottom: 10px; page-break-inside: avoid;'>")
//								   .append("<tr style='background-color: #f5f5f5;'>")
//								   .append("<td style='padding: 5px; border: 1px solid #ddd;'><b>").append(questionNo).append(" (").append(questionTypeStr).append(")").append("</b></td>")
//								   .append("</tr>")
//								   .append("<tr>")
//								   .append("<td style='padding: 5px; border: 1px solid #ddd;'>")
//								   .append(contentText)
//								   .append(answerSpace) // 添加预留的答题空间
//								   .append("</td>")
//								   .append("</tr>")
//								   .append("</table>");
//								  
//						}
//						
//						// 一次性转换整个 HTML 内容
//						ByteArrayOutputStream baos = new ByteArrayOutputStream();
//						HtmlConverter.convertToPdf(fullHtml.toString(), baos, properties);
//						byte[] pdfBytes = baos.toByteArray();
//						
//						// 将转换后的 PDF 内容添加到主文档
//						PdfDocument tempPdf = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdfBytes)));
//						tempPdf.copyPagesTo(1, tempPdf.getNumberOfPages(), pdfDoc);
//						tempPdf.close();
//						
//						// 添加分页
//						document.add(new Paragraph("\n"));
//					} else {
//						log("跳过考试: " + examName + " (发布时间: " + publishTimeStr + " 早于选择日期)");
//					}
//				}
//				
//				// 关闭文档
//				document.close();
//				log("PDF文档已关闭");
//				log("极课错题PDF生成完成: " + pdfPath);
//			} else {
//				log("响应码不是200: " + jsonResponse.get("code").getAsInt());
//			}
//		} catch (Exception e) {
//			log("处理极课错题JSON失败: " + e.getMessage());
//			e.printStackTrace();
//		}
//	}



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

    private void processJsonResponse_photoplus(String response,String number) {
        try {
            response = removeBom(response);
            log("跑野时刻-响应内容: " + response);
            
            // 解析 JSON 响应
            Gson gson = new Gson();
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            
            // 获取 result 对象
            JsonObject result = jsonResponse.getAsJsonObject("result");
            
            // 获取照片列表
            JsonArray photos = result.getAsJsonArray("pics_array");
            log("跑野时刻照片数量:" + photos.size());
            
            for (int i = 0; i < photos.size(); i++) {
                JsonObject photo = photos.get(i).getAsJsonObject();
                String photoUrl = "https:"+photo.get("origin_img").getAsString();
                String relate_time = photo.get("relate_time").getAsString();
                String fileName = photo.get("pic_name").getAsString();
//                String fileName = photoUrl.substring(photoUrl.lastIndexOf('/') + 1);
                log(photoUrl);
                downloadImage(photoUrl, "photoplus",number, fileName);
            }
            
            log("跑野时刻照片下载完成 准备生成压缩包");
            createZipFile("photoplus",number);
            
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
            
            // 根据 pathName 生成不同的压缩包文件名和文件夹名
            String zipFileName;
            String folderDisplayName;
            if ("runff".equals(pathName)) {
                zipFileName = folderName + "_跑步维生素.zip";
                folderDisplayName = folderName + "_跑步维生素";
            } else if ("runnerbar".equals(pathName)) {
                zipFileName = folderName + "_爱运动.zip";
                folderDisplayName = folderName + "_爱运动";
            } else if ("photoplus".equals(pathName)) {
                zipFileName = folderName + "_跑野时刻.zip";
                folderDisplayName = folderName + "_跑野时刻";                
            } else {
                zipFileName = folderName + ".zip";
                folderDisplayName = folderName;
            }
            
            Path zipFilePath = Paths.get(pathName, zipFileName);
            
            // 创建 ZIP 文件
            try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                    Files.newOutputStream(zipFilePath))) {
                
                // 遍历文件夹中的所有文件
                Files.walk(sourceFolderPath)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        try {
                            // 计算相对路径，使用新的文件夹名称
                            String relativePath = folderDisplayName + "/" + sourceFolderPath.relativize(path).toString();
                            
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
        try {
            // 确保日志消息使用UTF-8编码
            String encodedMessage = new String(message.getBytes("UTF-8"), "UTF-8");
            logArea.append(encodedMessage + "\n");
            // 滚动到最新内容
            logArea.setCaretPosition(logArea.getDocument().getLength());
        } catch (Exception e) {
            // 如果编码转换失败，直接输出原始消息
            logArea.append(message + "\n");
            // 滚动到最新内容
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }
    }
    
    public static void main(String[] args){


    }

	
} 

// 添加页脚处理器类
class PageFooterHandler implements IEventHandler {
    private PdfFont font;
    
    public PageFooterHandler(PdfFont font) {
        this.font = font;
    }
    
    @Override
    public void handleEvent(Event event) {
        PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
        PdfPage page = docEvent.getPage();
        int pageNumber = docEvent.getDocument().getPageNumber(page);
        PdfCanvas canvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), docEvent.getDocument());
        
        // 设置字体和颜色
        canvas.setFontAndSize(font, 10);
        canvas.setFillColor(DeviceGray.GRAY);
        
        // 计算页脚文本的位置（居中）
        float x = (page.getPageSize().getWidth() - 50) / 2;
        float y = 15;
        
        // 绘制页脚文本
        canvas.beginText();
        canvas.moveText(x, y);
        canvas.showText("第" + pageNumber + "页");
        canvas.endText();
        
        canvas.release();
    }
} 