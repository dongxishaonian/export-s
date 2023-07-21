package com.ytdevops.knowledgebase.service;

import com.ytdevops.knowledgebase.ImageInfoDto;
import com.ytdevops.knowledgebase.KnowledgeDocDto;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.exceptions.InvalidFormatException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.AltChunkType;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;
import org.w3c.tidy.Tidy;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Data
@Service
@RequestScope
public class KnowledgeBaseWordExportService {
    private final static Integer KNOWLEDGE_UPLOAD_CONNECTION_TIMEOUT = 5 * 1000;
    private final static Float IMAGE_DEFAULT_SIZE = 540F;
    private ThreadLocal<String> tmpDir = new ThreadLocal<>();
    private WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.createPackage();
    private MainDocumentPart mdp = wordMLPackage.getMainDocumentPart();

    public KnowledgeBaseWordExportService() throws InvalidFormatException {
    }


    @SneakyThrows
    public byte[] exportWord(KnowledgeDocDto knowledgeDocDto) {
        tmpDir.set("/tmp/" + UUID.randomUUID());
        try {
            FileUtils.forceMkdir(new File(tmpDir.get()));
            return generateWordBytes(knowledgeDocDto.getContentHtml());
        } catch (IOException e) {
            log.error("exportWord error:{}", ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("word导出失败，请联系Mappingspace客服！");
        } finally {
            FileUtils.deleteDirectory(new File(tmpDir.get()));
        }
    }

    public byte[] generateWordBytes(String contentHtml) {
        try {
            insertContent(contentHtml);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            WordprocessingMLPackage pkgOut = mdp.convertAltChunks();
            pkgOut.save(byteArrayOutputStream);

            return byteArrayOutputStream.toByteArray();
        } catch (Docx4JException e) {
            log.error("导出word出错！Error Message:{}", ExceptionUtils.getStackTrace(e));
            throw new RuntimeException(e);
        }
    }


    private void insertContent(String contentHtml) {
        if (StringUtils.isBlank(contentHtml)) {
            return;
        }

        Document content = Jsoup.parse(contentHtml);
        Elements tables = content.select("table");
        tables.forEach(table -> {
            table.attr("border", "1");
            table.attr("cellspacing", "0");
            table.attr("width", "60%");
            table.attr("style", "table-layout:fixed;");
            table.select("colgroup").remove();
        });

        Elements images = content.select("img");
        images.forEach(image -> {
            String imageUrl = image.attr("src");
            ImageInfoDto imageInfoDto = downloadImage(imageUrl);
            image.attr("src", "file://" + imageInfoDto.getImgPath());
            image.removeAttr("style");
            image.removeAttr("width");
        });
        insertHtmlToDoc(content.html());
    }


    private ImageInfoDto downloadImage(String imageUrl) {
        ImageInfoDto imageInfoDto = new ImageInfoDto();
        try {
            URL url = new URL(imageUrl);
            URLConnection urlConnection = url.openConnection();
            urlConnection.setConnectTimeout(KNOWLEDGE_UPLOAD_CONNECTION_TIMEOUT);
            InputStream inputStream = urlConnection.getInputStream();
            byte[] imageData = IOUtils.toByteArray(inputStream);
            String image = tmpDir.get() + "/" + UUID.randomUUID() + ".png";
            FileUtils.writeByteArrayToFile(new File(image), imageData);
            imageInfoDto.setImgPath(image);
        } catch (Exception e) {
            log.error("读取图片出错！失败链接:{} Error Message:{}", imageUrl, ExceptionUtils.getStackTrace(e));
            return new ImageInfoDto();
        }

        try {
            URL url = new URL(imageUrl);
            BufferedImage image = ImageIO.read(url);
            imageInfoDto.setWidth((float) image.getWidth()).setHeight((float) image.getHeight());
        } catch (Exception e) {
            log.error("读取图片大小出错！失败链接:{} Error Message:{}", imageUrl, ExceptionUtils.getStackTrace(e));
            return imageInfoDto.setWidth(IMAGE_DEFAULT_SIZE).setHeight(IMAGE_DEFAULT_SIZE);
        }
        imageInfoDto.getReasonableSize();

        return imageInfoDto;
    }


    private void insertHtmlToDoc(String html) {
        try {
            String xhtml = convertToXhtml(html);
            mdp.addAltChunk(AltChunkType.Xhtml, xhtml.getBytes());
            wordMLPackage = mdp.convertAltChunks();
            mdp = wordMLPackage.getMainDocumentPart();
        } catch (Docx4JException e) {
            log.error("addAltChunk error:{}", ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("导出Word失败!");
        }
    }

    private String convertToXhtml(String html) {
        Tidy tidy = new Tidy();
        tidy.setInputEncoding("UTF-8");
        tidy.setOutputEncoding("UTF-8");
        tidy.setXHTML(true);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        tidy.parseDOM(inputStream, outputStream);
        return outputStream.toString(StandardCharsets.UTF_8);
    }
}
