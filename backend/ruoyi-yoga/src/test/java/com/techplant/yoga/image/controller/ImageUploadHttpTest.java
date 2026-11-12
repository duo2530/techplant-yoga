package com.techplant.yoga.image.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techplant.yoga.common.testcontainers.AbstractYogaHttpIntegrationTest;
import com.techplant.yoga.common.testcontainers.YogaSchema;
import com.techplant.yoga.image.config.OssProperties;
import com.techplant.yoga.member.domain.MemberDO;

/**
 * 管理端图片上传接口的 HTTP 集成测试：真实管理端安全过滤链。
 *
 * <p>覆盖游客与会员不可上传、空文件／超限／非图片／扩展名与内容不符被拒绝；
 * 配置了 {@code YOGA_OSS_ACCESS_KEY_ID} 时真实上传到 OSS、匿名读回比对字节后删除对象，
 * 未配置时上传返回「图片存储未配置」。</p>
 */
@DisplayName("管理端图片上传接口（真实安全链：认证 / 格式与大小校验 / 真实 OSS 上传）")
class ImageUploadHttpTest extends AbstractYogaHttpIntegrationTest {
    private static final String UPLOAD_URL = "/admin/images";

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private OssProperties ossProperties;

    private String adminToken;

    private String memberToken;

    @BeforeEach
    void setUpData() {
        YogaSchema.reset(dataSource, "sys_user", "sys_role", "sys_user_role", "sys_menu", "sys_role_menu");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("INSERT INTO sys_role (role_id, role_name, role_key, role_sort, data_scope,"
                + " menu_check_strictly, dept_check_strictly, status, del_flag, create_by, create_time) VALUES"
                + " (3, '普通管理员', 'ordinary', 3, '1', 1, 1, '0', '0', 'admin', NOW())");
        jdbcTemplate.update("INSERT INTO sys_user (user_id, user_name, nick_name, user_type, email, phonenumber, sex,"
                + " avatar, password, status, del_flag, create_by, create_time) VALUES (101, 'plain_admin',"
                + " 'plain_admin', '00', '', '', '0', '', '', '0', '0', 'admin', NOW())");
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (101, 3)");
        adminToken = createAdminToken("plain_admin");

        MemberDO member = new MemberDO();
        member.setId(1856739201475235902L);
        member.setPhone("13800138000");
        memberToken = memberTokenService.createToken(member).getToken();
    }

    @Test
    @DisplayName("游客与会员：上传返回业务码 401")
    void upload_withoutAdminToken_shouldReturnUnauthorized() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", pngBytes());
        assertCode(multipart(UPLOAD_URL).file(file), 401);
        assertCode(multipart(UPLOAD_URL).file(file).header(AUTHORIZATION, bearer(memberToken)), 401);
    }

    @Test
    @DisplayName("缺少文件或空文件：返回 400")
    void upload_missingOrEmptyFile_shouldReturnBadRequest() throws Exception {
        assertRejected(multipart(UPLOAD_URL), "请选择要上传的图片");
        assertRejected(multipart(UPLOAD_URL).file(new MockMultipartFile("file", "a.png", "image/png", new byte[0])),
                "请选择要上传的图片");
    }

    @Test
    @DisplayName("超过 5MB：返回 400")
    void upload_oversize_shouldReturnBadRequest() throws Exception {
        byte[] content = Arrays.copyOf(pngBytes(), 5 * 1024 * 1024 + 1);
        assertRejected(multipart(UPLOAD_URL).file(new MockMultipartFile("file", "big.png", "image/png", content)),
                "图片大小不能超过 5MB");
    }

    @Test
    @DisplayName("扩展名不是 png/jpg/jpeg：返回 400")
    void upload_unsupportedExtension_shouldReturnBadRequest() throws Exception {
        assertRejected(multipart(UPLOAD_URL).file(new MockMultipartFile("file", "a.gif", "image/gif", pngBytes())),
                "仅支持 png、jpg、jpeg 格式的图片");
        assertRejected(multipart(UPLOAD_URL).file(new MockMultipartFile("file", "noext", "image/png", pngBytes())),
                "仅支持 png、jpg、jpeg 格式的图片");
    }

    @Test
    @DisplayName("内容不是图片或与扩展名不一致：返回 400")
    void upload_spoofedContent_shouldReturnBadRequest() throws Exception {
        byte[] text = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        assertRejected(multipart(UPLOAD_URL).file(new MockMultipartFile("file", "a.png", "image/png", text)),
                "文件内容不是有效的 png 或 jpg 图片");
        assertRejected(multipart(UPLOAD_URL).file(new MockMultipartFile("file", "a.jpg", "image/jpeg", pngBytes())),
                "图片扩展名与文件内容不一致");
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "YOGA_OSS_ACCESS_KEY_ID", matches = ".+")
    @DisplayName("未配置 OSS 访问密钥：合法图片返回 500「图片存储未配置」")
    void upload_withoutCredentials_shouldReportNotConfigured() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", pngBytes());
        JsonNode body = perform(multipart(UPLOAD_URL).file(file).header(AUTHORIZATION, bearer(adminToken)));
        assertEquals(500, body.get("code").asInt(), body.toString());
        assertEquals("图片存储未配置，暂时无法上传", body.get("msg").asText());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "YOGA_OSS_ACCESS_KEY_ID", matches = ".+")
    @DisplayName("真实 OSS：上传 PNG 返回公网 URL，匿名读回字节一致，结束后删除对象")
    void upload_png_shouldBePubliclyReadableFromOss() throws Exception {
        byte[] content = pngBytes();
        JsonNode body = perform(multipart(UPLOAD_URL)
                .file(new MockMultipartFile("file", "Avatar.PNG", "application/octet-stream", content))
                .header(AUTHORIZATION, bearer(adminToken)));
        assertEquals(200, body.get("code").asInt(), body.toString());
        String url = body.get("url").asText();
        assertEquals(url, body.get("fileName").asText(), "fileName 与 url 相同");

        String prefix = ossProperties.getPublicBaseUrl() + "/images/";
        assertTrue(url.startsWith(prefix), "URL 应位于公网地址前缀下：" + url);
        assertTrue(url.matches(".*/images/\\d{4}/\\d{2}/\\d{2}/[0-9a-f]{32}\\.png"), "对象键格式：" + url);
        String key = url.substring(ossProperties.getPublicBaseUrl().length() + 1);

        OSS oss = new OSSClientBuilder().build(ossProperties.getEndpoint(), ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret());
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            assertEquals(200, connection.getResponseCode(), "匿名读取图片");
            assertEquals("image/png", connection.getContentType());
            try (InputStream in = connection.getInputStream()) {
                assertArrayEquals(content, readAll(in), "读回字节与上传一致");
            }
        } finally {
            oss.deleteObject(ossProperties.getBucket(), key);
            oss.shutdown();
        }
    }

    private void assertRejected(MockHttpServletRequestBuilder request, String expectedMessage) throws Exception {
        JsonNode body = perform(request.header(AUTHORIZATION, bearer(adminToken)));
        assertEquals(400, body.get("code").asInt(), body.toString());
        assertEquals(expectedMessage, body.get("msg").asText());
    }

    private void assertCode(MockHttpServletRequestBuilder request, int expectedCode) throws Exception {
        mockMvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(expectedCode));
    }

    private JsonNode perform(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return JSON.readTree(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));
    }

    private static byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x4CAF50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
