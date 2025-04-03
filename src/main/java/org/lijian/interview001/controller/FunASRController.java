package org.lijian.interview001.controller;

import org.lijian.interview001.service.FunASRService;
import org.lijian.interview001.utils.AudioUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/api/asr")
public class FunASRController {

    private static final Logger log = LoggerFactory.getLogger(FunASRController.class);

    private final FunASRService funASRService;

    @Value("${parameters.fileUrl}")
    private String fileUploadPath;

    public FunASRController(FunASRService funASRService) {
        this.funASRService = funASRService;
    }

    /**
     * 上传音频文件并进行识别
     * @param file 音频文件
     * @return 识别结果
     */
    @PostMapping("/recognize")
    public ResponseEntity<?> recognizeAudio(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("请上传音频文件");
        }

        try {
            // 保存上传的文件
            String originalFilename = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFilename);
            String filename = UUID.randomUUID() + fileExtension;

            // 确保目录存在
            File uploadDir = new File(fileUploadPath);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            Path filePath = Paths.get(fileUploadPath, filename);
            file.transferTo(filePath.toFile());

            log.info("文件已上传至: {}", filePath);

            // 读取文件字节并转换为PCM格式
            byte[] audioData = Files.readAllBytes(filePath);

            // 转换音频格式
            File audioFile = filePath.toFile();
            if (AudioUtils.isSupportedAudioFormat(audioFile)) {
                audioData = AudioUtils.convertFileToPCM(audioFile);
                log.info("音频已转换为PCM格式");
            } else {
                log.warn("不支持的音频格式，将尝试直接识别");
            }

            // 发送到FunASR服务进行识别
            CompletableFuture<String> future = funASRService.recognizeAudio(audioData);

            // 等待识别结果
            String result = future.get(60, TimeUnit.SECONDS);

            // 返回识别结果
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("result", result);

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("文件处理失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("文件处理失败: " + e.getMessage());
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.error("语音识别失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("语音识别失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件扩展名
     * @param filename 文件名
     * @return 文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }

        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0) {
            return "";
        }

        return filename.substring(dotIndex);
    }
}