package com.ytdevops.file.rest;

import com.ytdevops.file.FileDetailDto;
import com.ytdevops.file.service.WordExportService;
import lombok.Data;
import org.apache.commons.io.IOUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/file")
@Data
public class FileExportResource {
    private final WordExportService wordExportService;

    @PostMapping("/word")
    public void exportWord(@RequestBody FileDetailDto fileDetailDto, HttpServletResponse response) throws IOException {
        byte[] bytes = wordExportService.exportWord(fileDetailDto);
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode(fileDetailDto.getData().get(0).getTopic(), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".docx");
        ServletOutputStream outputStream = response.getOutputStream();
        IOUtils.write(bytes, outputStream);
    }
}
