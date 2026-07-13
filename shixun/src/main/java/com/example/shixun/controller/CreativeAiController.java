package com.example.shixun.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/creative/ai")
@CrossOrigin(origins = "*")
public class CreativeAiController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    @Value("${siliconflow.api.key:}")
    private String siliconflowApiKey;

    @Value("${siliconflow.image.model:Kwai-Kolors/Kolors}")
    private String imageModel;

    @Value("${siliconflow.image.edit.model:Qwen/Qwen-Image-Edit-2509}")
    private String imageEditModel;

    @Value("${siliconflow.chat.model:Qwen/Qwen3-32B}")
    private String chatModel;

    public CreativeAiController(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @GetMapping("/styles")
    public List<Map<String, Object>> styles() {
        return jdbc.queryForList("SELECT id, name, description, base_prompt basePrompt, negative_prompt negativePrompt, palette, cultural_guardrails culturalGuardrails FROM brand_style_profile WHERE enabled=1 ORDER BY id");
    }

    @PostMapping("/prompt/compose")
    public Map<String, Object> composePrompt(@RequestBody GenerateImageRequest req) {
        Map<String, Object> style = style(req.styleId);
        String finalPrompt = buildPrompt(req.prompt, style, req.scene, req.productType);
        String negative = mergeNegative(req.negativePrompt, (String) style.get("negativePrompt"));
        return Map.of("prompt", finalPrompt, "negativePrompt", negative, "styleName", style.get("name"), "guardrails", style.get("culturalGuardrails") == null ? "" : style.get("culturalGuardrails"));
    }



    @PostMapping("/prompt/ai")
    public Map<String, Object> aiProductPrompt(@RequestBody GenerateImageRequest req) throws Exception {
        Map<String, Object> style = style(req.styleId);
        String system = "你是文创产品原型图提示词专家。你的任务是把用户的基础需求转成适合AI生图模型使用的高质量产品原型图Prompt。必须清晰、可执行、偏商业产品视觉，不要输出解释。";
        String user = "请根据以下信息生成一段用于AI生成文创产品原型图的中文提示词，并补充一段反向提示词。\n" +
                "作品/产品名：" + nullToEmpty(req.title) + "\n" +
                "产品类型：" + nullToEmpty(req.productType) + "\n" +
                "使用场景：" + nullToEmpty(req.scene) + "\n" +
                "用户想法：" + nullToEmpty(req.prompt) + "\n" +
                "品牌风格：" + style.get("name") + "；基础风格：" + style.get("basePrompt") + "\n" +
                "文化/版权要求：" + style.get("culturalGuardrails") + "\n\n" +
                "输出格式必须如下：\n" +
                "【正向提示词】\n" +
                "一段完整提示词，包含：产品主体、材质工艺、图案元素、构图、光线、背景、商业产品渲染、可打样细节。\n" +
                "【反向提示词】\n" +
                "一段反向提示词，包含：避免低清晰、变形、文字错误、廉价感、版权风险、杂乱背景等。";
        String content = callChat(system, user);
        String positive = content;
        String negative = mergeNegative(req.negativePrompt, (String) style.get("negativePrompt"));
        String posMark = "【正向提示词】";
        String negMark = "【反向提示词】";
        int pos = content.indexOf(posMark);
        int neg = content.indexOf(negMark);
        if (pos >= 0 && neg > pos) {
            positive = content.substring(pos + posMark.length(), neg).trim();
            String aiNegative = content.substring(neg + negMark.length()).trim();
            negative = mergeNegative(aiNegative, negative);
        }
        String finalPrompt = buildPrompt(positive, style, req.scene, req.productType);
        return Map.of(
                "prompt", finalPrompt,
                "rawPrompt", positive,
                "negativePrompt", negative,
                "styleName", style.get("name"),
                "source", "siliconflow:" + chatModel
        );
    }

    @PostMapping("/text-to-image")
    public Map<String, Object> textToImage(@RequestBody GenerateImageRequest req) throws Exception {
        Map<String, Object> style = style(req.styleId);
        String finalPrompt = buildPrompt(req.prompt, style, req.scene, req.productType);
        String negative = mergeNegative(req.negativePrompt, (String) style.get("negativePrompt"));
        String jobNo = no("AIG");
        Long jobId = createJob(jobNo, "text_to_image", "siliconflow", imageModel, req.styleId, null, finalPrompt, negative, "running", null, null);
        try {
            if (siliconflowApiKey == null || siliconflowApiKey.trim().isEmpty() || siliconflowApiKey.contains("YOUR_")) {
                throw new IllegalStateException("未配置 siliconflow.api.key，请在 shixun/application-local.properties 配置");
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", imageModel);
            payload.put("prompt", finalPrompt);
            payload.put("negative_prompt", negative);
            payload.put("image_size", req.imageSize == null ? "1024x1024" : req.imageSize);
            payload.put("batch_size", 1);
            if (req.seed != null) payload.put("seed", req.seed);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.siliconflow.cn/v1/images/generations"))
                    .header("Authorization", "Bearer " + siliconflowApiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("SiliconFlow HTTP " + response.statusCode() + ": " + response.body());
            JsonNode root = mapper.readTree(response.body());
            String remoteUrl = extractImageUrl(root);
            String localUrl = saveRemoteImage(remoteUrl, "ai-2d-", ".png");
            Long assetId = createAsset(req.title == null || req.title.isBlank() ? "AI生成图片" : req.title, "image", "ai_generated", localUrl, localUrl, finalPrompt, negative, req.styleId, null, "png", req.tags, Map.of("provider", "siliconflow", "model", imageModel, "remoteUrl", remoteUrl));
            jdbc.update("UPDATE ai_generation_job SET status='succeeded', output_asset_id=? WHERE id=?", assetId, jobId);
            return Map.of("jobNo", jobNo, "assetId", assetId, "imageUrl", localUrl, "previewUrl", localUrl, "fileUrl", localUrl, "prompt", finalPrompt, "negativePrompt", negative, "status", "succeeded", "source", "siliconflow:" + imageModel, "model", imageModel);
        } catch (Exception e) {
            jdbc.update("UPDATE ai_generation_job SET status='failed', error_message=? WHERE id=?", e.getMessage(), jobId);
            throw e;
        }
    }

    @PostMapping("/image-to-image")
    public Map<String, Object> imageToImage(@RequestBody GenerateImageRequest req) throws Exception {
        if (req.inputAssetId == null) throw new IllegalArgumentException("请先选择一张参考图");
        Map<String, Object> style = style(req.styleId);
        String finalPrompt = buildPrompt(req.prompt, style, req.scene, req.productType);
        String negative = mergeNegative(req.negativePrompt, (String) style.get("negativePrompt"));
        String jobNo = no("I2I");
        Long jobId = createJob(jobNo, "image_to_image", "siliconflow", imageEditModel, req.styleId, req.inputAssetId, finalPrompt, negative, "running", null, null);
        try {
            if (siliconflowApiKey == null || siliconflowApiKey.trim().isEmpty() || siliconflowApiKey.contains("YOUR_")) {
                throw new IllegalStateException("未配置 siliconflow.api.key，请在 shixun/application-local.properties 配置");
            }
            String inputImage = buildInputImageForSiliconFlow(req.inputAssetId);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", imageEditModel);
            payload.put("prompt", finalPrompt);
            payload.put("image", inputImage);
            if (negative != null && !negative.isBlank()) payload.put("negative_prompt", negative);
            payload.put("batch_size", 1);
            if (req.seed != null) payload.put("seed", req.seed);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.siliconflow.cn/v1/images/generations"))
                    .header("Authorization", "Bearer " + siliconflowApiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("SiliconFlow图改图 HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode root = mapper.readTree(response.body());
            String remoteUrl = extractImageUrl(root);
            String localUrl = saveRemoteImage(remoteUrl, "ai-i2i-", ".png");
            Long assetId = createAsset(
                    req.title == null || req.title.isBlank() ? "AI图改图作品" : req.title + "-图改图",
                    "image",
                    "ai_generated",
                    localUrl,
                    localUrl,
                    finalPrompt,
                    negative,
                    req.styleId,
                    req.inputAssetId,
                    "png",
                    req.tags == null || req.tags.isBlank() ? "图改图,AI生成,之间味道" : req.tags + ",图改图",
                    Map.of("provider", "siliconflow", "model", imageEditModel, "remoteUrl", remoteUrl, "inputAssetId", req.inputAssetId)
            );
            jdbc.update("UPDATE ai_generation_job SET status='succeeded', output_asset_id=? WHERE id=?", assetId, jobId);
            return Map.of("jobId", jobId, "jobNo", jobNo, "assetId", assetId, "imageUrl", localUrl, "prompt", finalPrompt, "negativePrompt", negative, "status", "succeeded");
        } catch (Exception e) {
            jdbc.update("UPDATE ai_generation_job SET status='failed', error_message=? WHERE id=?", e.getMessage(), jobId);
            throw e;
        }
    }

    @PostMapping("/text-to-3d")
    public Map<String, Object> textTo3d(@RequestBody Generate3dRequest req) throws Exception {
        String prompt = "3D cultural creative product model, " + nullToEmpty(req.prompt) + ", export-ready mesh, clean topology, product prototype";
        String jobNo = no("T3D");
        Long jobId = createJob(jobNo, "text_to_3d", "siliconflow", chatModel, null, req.inputAssetId, prompt, null, "running", null, req.exportFormats == null ? "OBJ,STL,GLB" : req.exportFormats);
        try {
            String spec = callChat(
                    "你是文创产品3D建模指导专家。硅基流动当前在本系统用于生成3D建模规格书，不直接产出OBJ/STL文件。请输出可交给建模师或后续3D工具的结构化建模方案。",
                    "产品/创意：" + prompt + "\n" +
                    "参考资产ID：" + req.inputAssetId + "\n" +
                    "导出格式：" + (req.exportFormats == null ? "OBJ,STL,GLB" : req.exportFormats) + "\n" +
                    "请包含：造型拆解、尺寸建议、材质、工艺、建模步骤、打印/开模风险。"
            );
            Long assetId = createAsset("AI 3D建模规格书", "prompt", "ai_generated", null, null, spec, null, null, req.inputAssetId, "txt", "3D建模,硅基流动,文创打样", Map.of("provider", "siliconflow", "model", chatModel));
            jdbc.update("UPDATE ai_generation_job SET status='succeeded', output_asset_id=? WHERE id=?", assetId, jobId);
            return Map.of("jobId", jobId, "jobNo", jobNo, "status", "succeeded", "assetId", assetId, "prompt", prompt, "aiDraft", spec, "source", "siliconflow:" + chatModel, "exportFormats", req.exportFormats == null ? "OBJ,STL,GLB" : req.exportFormats, "message", "已通过硅基流动生成3D建模规格书；如需真实OBJ/STL，后续仍需接入专业3D生成/建模工具。 ");
        } catch (Exception e) {
            jdbc.update("UPDATE ai_generation_job SET status='failed', error_message=? WHERE id=?", e.getMessage(), jobId);
            throw e;
        }
    }

    @PostMapping(value = "/assets/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadAsset(@RequestParam("file") MultipartFile file,
                                           @RequestParam(required = false) String title,
                                           @RequestParam(required = false) String tags) throws Exception {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择要上传的图片");
        String original = file.getOriginalFilename() == null ? "upload.png" : file.getOriginalFilename();
        String lower = original.toLowerCase(Locale.ROOT);
        String ext = lower.endsWith(".jpg") || lower.endsWith(".jpeg") ? ".jpg" : lower.endsWith(".webp") ? ".webp" : ".png";
        if (!(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp"))) {
            throw new IllegalArgumentException("当前仅支持 PNG/JPG/WEBP 图片");
        }
        Path dir = Path.of(System.getProperty("user.dir"), "..", "shixun-vue", "public", "uploads").normalize().toAbsolutePath();
        Files.createDirectories(dir);
        String fileName = "ref-" + System.currentTimeMillis() + ext;
        Path target = dir.resolve(fileName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        String url = "/uploads/" + fileName;
        Long assetId = createAsset(
                title == null || title.isBlank() ? original : title,
                "image",
                "upload",
                url,
                url,
                "用户上传参考图，可用于图生图或3D建模参考。",
                null,
                null,
                null,
                ext.replace(".", ""),
                tags == null || tags.isBlank() ? "参考图,上传" : tags,
                Map.of("uploadName", original, "size", file.getSize(), "contentType", file.getContentType() == null ? "" : file.getContentType())
        );
        return Map.of("assetId", assetId, "url", url, "title", title == null || title.isBlank() ? original : title);
    }

    @GetMapping("/assets/{id}/content")
    public ResponseEntity<byte[]> assetContent(@PathVariable Long id) throws Exception {
        Map<String,Object> asset=jdbc.queryForMap("SELECT file_url fileUrl,preview_url previewUrl,format FROM digital_asset WHERE id=?",id);
        String url=String.valueOf(asset.get("fileUrl")==null?asset.get("previewUrl"):asset.get("fileUrl"));
        if(url.startsWith("http://")||url.startsWith("https://")) {
            HttpResponse<byte[]> response=http.send(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            if(response.statusCode()<200||response.statusCode()>=300) throw new IOException("读取图片失败 HTTP "+response.statusCode());
            String ct=response.headers().firstValue("content-type").orElse("image/png");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(ct)).body(response.body());
        }
        Path publicDir=Path.of(System.getProperty("user.dir"),"..","shixun-vue","public").normalize().toAbsolutePath();
        String relative=url.startsWith("/")?url.substring(1):url; Path file=publicDir.resolve(relative).normalize();
        if(!file.startsWith(publicDir)||!Files.exists(file)) throw new IOException("图片文件不存在："+url);
        String lower=file.getFileName().toString().toLowerCase(Locale.ROOT);
        MediaType type=lower.endsWith(".jpg")||lower.endsWith(".jpeg")?MediaType.IMAGE_JPEG:lower.endsWith(".webp")?MediaType.parseMediaType("image/webp"):MediaType.IMAGE_PNG;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(type).body(Files.readAllBytes(file));
    }

    @GetMapping("/assets")
    public List<Map<String, Object>> assets(@RequestParam(required = false) String type) {
        if (type != null && !type.isBlank()) return jdbc.queryForList("SELECT id, asset_no assetNo, title, asset_type assetType, source_type sourceType, file_url fileUrl, preview_url previewUrl, prompt, style_id styleId, parent_asset_id parentAssetId, version_no versionNo, status, format, tags, created_at createdAt FROM digital_asset WHERE asset_type=? ORDER BY id DESC", type);
        return jdbc.queryForList("SELECT id, asset_no assetNo, title, asset_type assetType, source_type sourceType, file_url fileUrl, preview_url previewUrl, prompt, style_id styleId, parent_asset_id parentAssetId, version_no versionNo, status, format, tags, created_at createdAt FROM digital_asset ORDER BY id DESC LIMIT 100");
    }

    @PostMapping("/reviews")
    public Map<String, Object> createReview(@RequestBody ReviewRequest req) throws Exception {
        if (req.assetId == null) throw new IllegalArgumentException("assetId不能为空");
        Map<String, Object> asset = jdbc.queryForMap("SELECT id, asset_no assetNo, title, asset_type assetType, file_url fileUrl, prompt, tags, metadata_json metadataJson FROM digital_asset WHERE id=?", req.assetId);
        String reviewNo = no("REV");
        Long reviewId = insertReview(reviewNo, req.assetId);

        List<Map<String, String>> agents = List.of(
                Map.of("key", "senior_designer", "name", "资深设计师", "focus", "视觉构图、品牌调性、文化符号准确性、可延展为文创IP的设计完成度"),
                Map.of("key", "market_analyst", "name", "市场分析师", "focus", "目标人群、卖点清晰度、差异化、上架转化潜力和传播话题性"),
                Map.of("key", "cost_controller", "name", "成本控制专家", "focus", "打样难度、印刷/制造成本、SKU适配性、量产风险和库存压力"),
                Map.of("key", "target_consumer", "name", "目标消费者", "focus", "第一眼吸引力、情绪价值、购买理由、送礼/自用场景和价格接受度")
        );
        List<Map<String, Object>> results = new ArrayList<>();
        int total = 0;
        for (Map<String, String> agent : agents) {
            Map<String, Object> one = reviewByAgent(agent, asset, req.context);
            total += ((Number) one.get("score")).intValue();
            insertAgentReview(reviewId, agent, one);
            results.add(new LinkedHashMap<>(Map.of(
                    "agentKey", agent.get("key"),
                    "agentName", agent.get("name"),
                    "score", one.get("score"),
                    "verdict", one.get("verdict"),
                    "comments", one.get("comments"),
                    "suggestions", one.get("suggestions")
            )));
        }
        BigDecimal avg = BigDecimal.valueOf(total).divide(BigDecimal.valueOf(results.size()), 2, java.math.RoundingMode.HALF_UP);
        String recommendation = avg.intValue() >= 85 ? "go" : avg.intValue() >= 70 ? "adjust" : "reject";
        String summary = buildReviewSummary(avg, recommendation, results);
        jdbc.update("UPDATE design_review SET overall_score=?, summary=?, recommendation=? WHERE id=?", avg, summary, recommendation, reviewId);
        return Map.of("reviewId", reviewId, "reviewNo", reviewNo, "asset", asset, "overallScore", avg, "recommendation", recommendation, "summary", summary, "agents", results);
    }

    @GetMapping("/reviews")
    public List<Map<String, Object>> reviews(@RequestParam(required = false) Long assetId) {
        String sql = "SELECT r.id, r.review_no reviewNo, r.asset_id assetId, a.title assetTitle, a.preview_url previewUrl, r.overall_score overallScore, r.summary, r.recommendation, r.created_at createdAt FROM design_review r JOIN digital_asset a ON r.asset_id=a.id";
        List<Map<String, Object>> list;
        if (assetId != null) list = jdbc.queryForList(sql + " WHERE r.asset_id=? ORDER BY r.id DESC", assetId);
        else list = jdbc.queryForList(sql + " ORDER BY r.id DESC LIMIT 50");
        for (Map<String, Object> r : list) {
            r.put("agents", jdbc.queryForList("SELECT agent_key agentKey, agent_name agentName, score, verdict, comments, suggestions_json suggestionsJson FROM design_review_agent WHERE review_id=? ORDER BY id", r.get("id")));
        }
        return list;
    }

    @GetMapping("/jobs")
    public List<Map<String, Object>> jobs() {
        return jdbc.queryForList("SELECT id, job_no jobNo, job_type jobType, provider, model_name modelName, input_asset_id inputAssetId, output_asset_id outputAssetId, status, error_message errorMessage, export_formats exportFormats, created_at createdAt FROM ai_generation_job ORDER BY id DESC LIMIT 100");
    }

    private Long insertReview(String reviewNo, Long assetId) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO design_review (review_no, asset_id) VALUES (?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, reviewNo); ps.setLong(2, assetId); return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    private void insertAgentReview(Long reviewId, Map<String, String> agent, Map<String, Object> result) throws Exception {
        jdbc.update("INSERT INTO design_review_agent (review_id, agent_key, agent_name, score, verdict, comments, suggestions_json) VALUES (?,?,?,?,?,?,?)",
                reviewId, agent.get("key"), agent.get("name"), result.get("score"), result.get("verdict"), result.get("comments"), mapper.writeValueAsString(result.get("suggestions")));
    }

    private Map<String, Object> reviewByAgent(Map<String, String> agent, Map<String, Object> asset, String context) {
        String instruction = "你是“之间味道”文创设计售卖平台AI评审团成员：" + agent.get("name") + "。你的评审重点：" + agent.get("focus") + "。请评审一个图片类文创设计方案。必须只返回JSON，不要markdown。格式：{\"score\":0-100整数,\"verdict\":\"一句话结论\",\"comments\":\"具体评语\",\"suggestions\":[\"建议1\",\"建议2\",\"建议3\"]}";
        String user = "设计资产标题：" + asset.get("title") + "\n资产类型：" + asset.get("assetType") + "\n标签：" + asset.get("tags") + "\n生成/设计Prompt：" + asset.get("prompt") + "\n图片地址：" + asset.get("fileUrl") + "\n补充业务背景：" + (context == null ? "用于图片IP文创产品开发，可衍生明信片、装饰画、手机壳、帆布袋等SKU。" : context);
        try {
            String content = callChat(instruction, user);
            return parseAgentJson(content);
        } catch (Exception e) {
            return fallbackReview(agent, asset, e.getMessage());
        }
    }

    private String callChat(String system, String user) throws Exception {
        if (siliconflowApiKey == null || siliconflowApiKey.trim().isEmpty()) throw new IllegalStateException("未配置siliconflow.api.key");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", chatModel);
        payload.put("temperature", 0.35);
        payload.put("max_tokens", 700);
        payload.put("enable_thinking", false);
        payload.put("messages", List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", user)));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.siliconflow.cn/v1/chat/completions"))
                .header("Authorization", "Bearer " + siliconflowApiKey.trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("SiliconFlow Chat HTTP " + response.statusCode() + ": " + response.body());
        JsonNode root = mapper.readTree(response.body());
        return root.path("choices").path(0).path("message").path("content").asText();
    }

    private Map<String, Object> parseAgentJson(String content) throws Exception {
        String c = content.trim();
        int start = c.indexOf('{'); int end = c.lastIndexOf('}');
        if (start >= 0 && end > start) c = c.substring(start, end + 1);
        JsonNode n = mapper.readTree(c);
        int score = Math.max(0, Math.min(100, n.path("score").asInt(75)));
        List<String> suggestions = new ArrayList<>();
        if (n.path("suggestions").isArray()) n.path("suggestions").forEach(x -> suggestions.add(x.asText()));
        return new LinkedHashMap<>(Map.of(
                "score", score,
                "verdict", n.path("verdict").asText("建议进一步优化"),
                "comments", n.path("comments").asText("该方案具备一定文创开发潜力。"),
                "suggestions", suggestions
        ));
    }

    private Map<String, Object> fallbackReview(Map<String, String> agent, Map<String, Object> asset, String err) {
        return new LinkedHashMap<>(Map.of(
                "score", 72,
                "verdict", "已完成基础评审，建议人工复核",
                "comments", agent.get("name") + "认为该方案可进入初步讨论；AI评审调用异常：" + err,
                "suggestions", List.of("明确目标SKU与使用场景", "补充视觉主次层级", "进行小样打样与用户反馈")
        ));
    }

    private String buildReviewSummary(BigDecimal avg, String recommendation, List<Map<String, Object>> results) {
        String rec = "go".equals(recommendation) ? "建议进入商品化打样" : "adjust".equals(recommendation) ? "建议优化后再打样" : "暂不建议进入生产";
        return "AI评审团平均分 " + avg + "，结论：" + rec + "。重点关注设计表达、市场卖点、成本可行性与消费者购买理由四个维度。";
    }

    private Map<String, Object> style(Long id) { return jdbc.queryForMap("SELECT id, name, base_prompt basePrompt, negative_prompt negativePrompt, cultural_guardrails culturalGuardrails FROM brand_style_profile WHERE id=?", id == null ? 1L : id); }

    private String buildPrompt(String userPrompt, Map<String, Object> style, String scene, String productType) {
        StringBuilder sb = new StringBuilder();
        sb.append(style.get("basePrompt"));
        if (scene != null && !scene.isBlank()) sb.append(", scene: ").append(scene.trim());
        if (productType != null && !productType.isBlank()) sb.append(", designed for ").append(productType.trim());
        sb.append(", brand name: Between Taste, premium cultural creative product visual, high detail, commercial-ready");
        if (userPrompt != null && !userPrompt.isBlank()) sb.append(", user concept: ").append(userPrompt.trim());
        Object guard = style.get("culturalGuardrails");
        if (guard != null) sb.append(", cultural guardrails: ").append(guard);
        return sb.toString();
    }

    private String mergeNegative(String userNegative, String styleNegative) {
        if (userNegative == null || userNegative.isBlank()) return styleNegative == null ? "" : styleNegative;
        if (styleNegative == null || styleNegative.isBlank()) return userNegative;
        return styleNegative + ", " + userNegative;
    }

    private String extractImageUrl(JsonNode root) {
        JsonNode data = root.get("data");
        if (data != null && data.isArray() && data.size() > 0 && data.get(0).hasNonNull("url")) return data.get(0).get("url").asText();
        JsonNode images = root.get("images");
        if (images != null && images.isArray() && images.size() > 0 && images.get(0).hasNonNull("url")) return images.get(0).get("url").asText();
        throw new IllegalStateException("无法从SiliconFlow响应中解析图片URL: " + root);
    }

    private String buildInputImageForSiliconFlow(Long assetId) throws IOException {
        Map<String, Object> asset = jdbc.queryForMap("SELECT file_url fileUrl, preview_url previewUrl, format FROM digital_asset WHERE id=?", assetId);
        String url = String.valueOf(asset.get("fileUrl") == null ? asset.get("previewUrl") : asset.get("fileUrl"));
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        Path publicDir = Path.of(System.getProperty("user.dir"), "..", "shixun-vue", "public").normalize().toAbsolutePath();
        String relative = url.startsWith("/") ? url.substring(1) : url;
        Path file = publicDir.resolve(relative).normalize();
        if (!file.startsWith(publicDir) || !Files.exists(file)) throw new IOException("参考图文件不存在：" + url);
        String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
        String mime = lower.endsWith(".jpg") || lower.endsWith(".jpeg") ? "image/jpeg" : lower.endsWith(".webp") ? "image/webp" : "image/png";
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(file));
    }

    private String saveRemoteImage(String url, String prefix, String suffix) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IOException("下载生成图片失败 HTTP " + response.statusCode());
        Path dir = Path.of("..", "shixun-vue", "public", "generated").normalize();
        Files.createDirectories(dir);
        String file = prefix + System.currentTimeMillis() + suffix;
        Files.write(dir.resolve(file), response.body());
        return "/generated/" + file;
    }

    private Long createAsset(String title, String type, String sourceType, String fileUrl, String previewUrl, String prompt, String negative, Long styleId, Long parentAssetId, String format, String tags, Map<String, Object> meta) throws Exception {
        KeyHolder kh = new GeneratedKeyHolder();
        String assetNo = no("AST");
        String metaJson = mapper.writeValueAsString(meta == null ? Map.of() : meta);
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO digital_asset (asset_no,title,asset_type,source_type,file_url,preview_url,prompt,negative_prompt,style_id,parent_asset_id,format,tags,metadata_json,status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, assetNo); ps.setString(2, title); ps.setString(3, type); ps.setString(4, sourceType == null ? "ai_generated" : sourceType); ps.setString(5, fileUrl); ps.setString(6, previewUrl); ps.setString(7, prompt); ps.setString(8, negative); if (styleId == null) ps.setNull(9, java.sql.Types.BIGINT); else ps.setLong(9, styleId); if (parentAssetId == null) ps.setNull(10, java.sql.Types.BIGINT); else ps.setLong(10, parentAssetId); ps.setString(11, format); ps.setString(12, tags); ps.setString(13, metaJson); ps.setString(14, "draft");
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    private Long createJob(String jobNo, String type, String provider, String model, Long styleId, Long inputAssetId, String prompt, String negative, String status, String error, String exportFormats) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO ai_generation_job (job_no,job_type,provider,model_name,style_id,input_asset_id,prompt,negative_prompt,status,error_message,export_formats) VALUES (?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, jobNo); ps.setString(2, type); ps.setString(3, provider); ps.setString(4, model); if(styleId==null) ps.setNull(5, java.sql.Types.BIGINT); else ps.setLong(5, styleId); if(inputAssetId==null) ps.setNull(6, java.sql.Types.BIGINT); else ps.setLong(6, inputAssetId); ps.setString(7, prompt); ps.setString(8, negative); ps.setString(9, status); ps.setString(10, error); ps.setString(11, exportFormats);
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    private String no(String prefix) { return prefix + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + (int)(Math.random()*900+100); }
    private String nullToEmpty(String s) { return s == null ? "" : s; }

    public static class ReviewRequest {
        public Long assetId;
        public String context;
    }

    public static class GenerateImageRequest {
        public String title;
        public String prompt;
        public String negativePrompt;
        public Long styleId;
        public String scene;
        public String productType;
        public String imageSize;
        public Long seed;
        public String tags;
        public Long inputAssetId;
    }
    public static class Generate3dRequest {
        public String prompt;
        public Long inputAssetId;
        public String exportFormats;
    }
}
