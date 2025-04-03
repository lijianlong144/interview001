# FunASR Spring Boot 客户端

这是一个基于 Spring Boot 3.0 的 FunASR 客户端实现，用于连接 FunASR 语音识别服务并进行音频识别。

## 项目说明

本项目基于 [FunASR](https://github.com/modelscope/FunASR) 开源项目，通过 WebSocket 与 FunASR 服务端进行通信，实现了音频识别功能。

## 技术栈

- Spring Boot 3.0+
- WebSocket
- Java 17+

## 项目结构

```
src/main/java/com/example/lijian/
├── config/
│   └── WebSocketConfig.java            # WebSocket客户端配置
├── controller/
│   └── FunASRController.java           # 提供REST API接口
├── service/
│   └── FunASRService.java              # 封装FunASR服务调用
└── utils/
    └── AudioUtils.java                 # 音频处理工具类

src/main/resources/
└── application.yml                     # 配置文件
```

## 配置说明

在 `application.yml` 文件中包含以下配置：

```yaml
parameters:
  model: "offline"                      # 模式：offline/online/2pass
  hotWords: "{"关键词1":20,"关键词2":20}" # 热词配置
  fileUrl: "E:/project/Tool/file"       # 文件上传路径
  serverIpPort: "ws://127.0.0.1:10096"  # FunASR服务地址
```

## 接口说明

### 1. 音频识别接口

**路径**：`/api/asr/recognize`

**方法**：POST

**参数**：

- file：音频文件（支持常见的音频格式如wav, mp3等）

**返回**：

```json
{
  "success": true,
  "result": "识别的文本内容"
}
```

## 使用方法

### 1. 确保FunASR服务已启动

确保FunASR服务在配置的地址上可用（默认为`ws://127.0.0.1:10096`）。

### 2. 启动Spring Boot项目

```bash
mvn spring-boot:run
```

### 3. 调用API进行语音识别

使用HTTP客户端或前端应用调用：

```bash
curl -X POST -F "file=@/path/to/your/audio.wav" http://localhost:18080/api/asr/recognize
```

## 代码示例

### 前端调用示例

```html
<!DOCTYPE html>
<html>
<head>
    <title>语音识别演示</title>
    <script src="https://cdn.jsdelivr.net/npm/axios/dist/axios.min.js"></script>
</head>
<body>
    <h1>FunASR 语音识别</h1>
    
    <input type="file" id="audioFile" accept="audio/*">
    <button onclick="uploadAudio()">识别音频</button>
    
    <div id="result" style="margin-top: 20px; padding: 10px; border: 1px solid #ccc; min-height: 100px;">
        识别结果将显示在这里...
    </div>

    <script>
        function uploadAudio() {
            const fileInput = document.getElementById('audioFile');
            const resultDiv = document.getElementById('result');
            
            if (!fileInput.files || fileInput.files.length === 0) {
                alert('请选择一个音频文件');
                return;
            }
            
            const formData = new FormData();
            formData.append('file', fileInput.files[0]);
            
            resultDiv.innerHTML = '识别中，请稍候...';
            
            axios.post('/api/asr/recognize', formData, {
                headers: {
                    'Content-Type': 'multipart/form-data'
                }
            })
            .then(response => {
                if (response.data.success) {
                    resultDiv.innerHTML = response.data.result;
                } else {
                    resultDiv.innerHTML = '识别失败: ' + response.data.message;
                }
            })
            .catch(error => {
                resultDiv.innerHTML = '请求失败: ' + (error.response ? error.response.data : error.message);
            });
        }
    </script>
</body>
</html>
```

## 注意事项

1. 音频格式支持：
    - 推荐使用 16kHz、16bit、单声道的 PCM 格式，以获得最佳识别效果
    - 其他格式将尝试自动转换，但可能影响识别准确性

2. 并发处理：
    - 服务支持并发处理多个识别请求，使用UUID避免请求冲突

3. 超时设置：
    - 默认识别超时为60秒，可根据需要调整

## 常见问题

1. **连接失败**：
    - 检查FunASR服务是否正常运行
    - 确认服务地址配置是否正确

2. **识别结果不准确**：
    - 检查音频质量
    - 试着添加领域相关的热词
    - 调整音频格式为推荐的16kHz、16bit、单声道

3. **内存溢出**：
    - 对于大型音频文件，可能需要调整JVM内存设置

## 扩展与优化

1. 添加流式识别支持
2. 实现声纹识别
3. 添加多语言支持
4. 优化音频预处理
5. 添加缓存机制提高性能