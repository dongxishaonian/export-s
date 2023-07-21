package com.ytdevops.knowledgebase.rest;

import com.ytdevops.knowledgebase.HtmlToPdfDto;
import com.ytdevops.knowledgebase.KnowledgeDocDto;
import com.ytdevops.knowledgebase.service.KnowledgeBaseWordExportService;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/knowledgeBase")
@Data
@Slf4j
public class KnowledgeBaseExportResource {
    private final KnowledgeBaseWordExportService knowledgeBaseWordExportService;
    public static final String TMP_HTML_TO_PDF = "/tmp/htmlToPdf/";

    @PostMapping("/word")
    public void exportWord(@RequestBody KnowledgeDocDto knowledgeDocDto, HttpServletResponse response) throws IOException {
        byte[] bytes = knowledgeBaseWordExportService.exportWord(knowledgeDocDto);
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + UUID.randomUUID() + ".docx");
        ServletOutputStream outputStream = response.getOutputStream();
        IOUtils.write(bytes, outputStream);
    }

    @SneakyThrows
    @PostMapping("/pdf")
    public void htmlToPdf(@RequestBody HtmlToPdfDto htmlToPdfDto, HttpServletResponse response) {
        File htmlFile = new File(TMP_HTML_TO_PDF + UUID.randomUUID() + ".html");
        File pdfFile = new File(TMP_HTML_TO_PDF + UUID.randomUUID() + ".pdf");
        try {
            FileUtils.write(htmlFile, htmlToPdfDto.getContentHtml(), StandardCharsets.UTF_8);
            ProcessBuilder builder = new ProcessBuilder();
            String command = String.format("wkhtmltopdf %s %s", htmlFile.getAbsolutePath(), pdfFile.getAbsolutePath());
            log.info(command);
            builder.command("sh", "-c", command);
            builder.directory(new File("/"));
            Process process = builder.start();
            log.info(IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8));
            log.error(IOUtils.toString(process.getErrorStream(), StandardCharsets.UTF_8));
            byte[] fileByteArray = FileUtils.readFileToByteArray(pdfFile);
            response.setCharacterEncoding("utf-8");
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + UUID.randomUUID() + ".pdf");
            ServletOutputStream outputStream = response.getOutputStream();
            IOUtils.write(fileByteArray, outputStream);
        } catch (Exception e) {
            log.error("htmlToPdf error:{}", ExceptionUtils.getStackTrace(e));
        } finally {
            FileUtils.forceDelete(htmlFile);
            FileUtils.forceDelete(pdfFile);
        }
    }
}
