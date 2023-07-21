package com.ytdevops.file.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ytdevops.file.FieldType;
import com.ytdevops.file.FileDetailDto;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.commons.collections4.CollectionUtils;
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
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;
import org.w3c.tidy.Tidy;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Data
@Service
@RequestScope
public class WordExportService {
    private final static Integer KNOWLEDGE_UPLOAD_CONNECTION_TIMEOUT = 5 * 1000;
    private static final String HTML_TEMPLATE = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body></body></html>";
    private static final String BASE64_DEFAULT_HEAD = "data:image/png;base64,";
    private final static String FILE_EXPORT_OSS_FOLDER = "fileWordExport";
    private final static Integer MAX_HEADING_LEVEL = 9;
    private final static String SVG_HTML_HEAD = "<!DOCTYPE svg [<!ENTITY nbsp \"&#160;\">]>";
    private ThreadLocal<String> tmpDir = new ThreadLocal<>();
    private WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.createPackage();
    private MainDocumentPart mdp = wordMLPackage.getMainDocumentPart();

    public WordExportService() throws InvalidFormatException {
    }

    @SneakyThrows
    public byte[] exportWord(FileDetailDto fileDetailDto) {
        tmpDir.set("/tmp/" + UUID.randomUUID());
        try {
            FileUtils.forceMkdir(new File(tmpDir.get()));
            return generateWordBytes(fileDetailDto);
        } catch (IOException e) {
            log.error("exportWord error:{}", ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("word导出失败，请联系Mappingspace客服！");
        } finally {
            FileUtils.deleteDirectory(new File(tmpDir.get()));
        }
    }

    public byte[] generateWordBytes(FileDetailDto fileDetailDto) {
        try {
            List<FileDetailDto.Data> dataList = fileDetailDto.getData();
            FileDetailDto.Data rootData = dataList.stream().filter(f -> f.getId().equals("root")).findFirst().orElseThrow(() -> new RuntimeException("文件中无根节点！"));

            insertDescription(rootData);
            insertDrawIoDiagrams(rootData);

            List<FileDetailDto.Data> directChildren = dataList.stream().filter(f -> f.getParentId().equals(rootData.getId())).collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(directChildren)) {
                insertChildrenContent(dataList, directChildren);
            }
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            WordprocessingMLPackage pkgOut = mdp.convertAltChunks();
            pkgOut.save(byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        } catch (Exception e) {
            log.error("word export error : {}", ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("导出Word失败!");
        }
    }

    private void insertChildrenContent(List<FileDetailDto.Data> dataList, List<FileDetailDto.Data> directChildren) {
        directChildren.forEach(child -> {
            mdp.addStyledParagraphOfText("Heading" + Math.min(child.getLayer() - 1, MAX_HEADING_LEVEL), child.getTopic());
            insertDescription(child);
            insertDrawIoDiagrams(child);
            List<FileDetailDto.Data> childChildren = dataList.stream().filter(f -> f.getParentId().equals(child.getId())).collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(childChildren)) {
                insertChildrenContent(dataList, childChildren);
            }
        });
    }

    private void insertDescription(FileDetailDto.Data data) {
        if (StringUtils.isBlank(data.getDescription())) {
            return;
        }
        Document document = Jsoup.parse(HTML_TEMPLATE);
        Element body = document.body();

        Document description = Jsoup.parse(data.getDescription());
        Elements tables = description.select("table");
        tables.forEach(table -> {
            table.attr("border", "1");
            table.attr("cellspacing", "0");
            table.attr("width", "60%");
            table.attr("style", "table-layout:fixed;");
            table.select("colgroup").remove();
        });

        Elements images = description.select("img");
        images.forEach(image -> {
            String imageUrl = image.attr("src");
            String imagePath = downloadImage(imageUrl);
            image.attr("src", imagePath);
            image.removeAttr("style");
            image.removeAttr("width");
        });
        body.append(description.html());
        insertHtmlToDoc(document.html());
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

    private void insertDrawIoDiagrams(FileDetailDto.Data data) {
        List<FileDetailDto.ArchitectureDiagram> drawIoDiagramList = CollectionUtils.isEmpty(data.getArchitectureDiagrams()) ? new ArrayList<>() : data.getArchitectureDiagrams();
        if (CollectionUtils.isNotEmpty(getDrawIoInCustomFields(data))) {
            drawIoDiagramList.addAll(getDrawIoInCustomFields(data));
        }

        Document diagrams = Jsoup.parse(HTML_TEMPLATE);
        Element body = diagrams.body();
        if (CollectionUtils.isNotEmpty(drawIoDiagramList)) {
            drawIoDiagramList.forEach(diagram -> {
                String imagePath = downloadDiagram(diagram.getData());
                body.append(String.format("<p><img src=\"%s\"></p>", imagePath));
            });
            insertHtmlToDoc(diagrams.outerHtml());
        }
    }

    private List<FileDetailDto.ArchitectureDiagram> getDrawIoInCustomFields(FileDetailDto.Data data) {
        List<FileDetailDto.Content> contentList = data.getContent();
        if (CollectionUtils.isEmpty(contentList)) {
            return new ArrayList<>();
        }

        List<FileDetailDto.Content> drawIoContentList = contentList.stream().filter(f -> f.getFieldType().equals(FieldType.DRAWIO) && Objects.nonNull(f.getValue())).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(drawIoContentList)) {
            return new ArrayList<>();
        }
        List<String> drawIoStringValue = drawIoContentList.stream().map(FileDetailDto.Content::getStringValue).filter(StringUtils::isNotBlank).collect(Collectors.toList());

        return drawIoStringValue.stream().flatMap(fm -> {
            List<FileDetailDto.ArchitectureDiagram> diagrams;
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                diagrams = objectMapper.readValue(fm, new TypeReference<List<FileDetailDto.ArchitectureDiagram>>() {
                });
            } catch (JsonProcessingException e) {
                log.error("Get drawIo in custom fields error: {}", ExceptionUtils.getStackTrace(e));
                throw new RuntimeException("读取架构图失败！");
            }
            return diagrams.stream();
        }).collect(Collectors.toList());
    }

    private String downloadImage(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            URLConnection urlConnection = url.openConnection();
            urlConnection.setConnectTimeout(KNOWLEDGE_UPLOAD_CONNECTION_TIMEOUT);
            InputStream inputStream = urlConnection.getInputStream();
            byte[] imageData = IOUtils.toByteArray(inputStream);
            String image = tmpDir.get() + "/" + UUID.randomUUID() + ".png";
            FileUtils.writeByteArrayToFile(new File(image), imageData);
            return image;
        } catch (Exception e) {
            log.error("读取图片出错！失败链接{} Error Message:{}", imageUrl, ExceptionUtils.getStackTrace(e));
            return "";
        }
    }

    private String downloadDiagram(String drawIoUrl) {
        String diagramBase64;
        byte[] pngBytes;
        try {
            URL url = new URL(drawIoUrl);
            URLConnection urlConnection = url.openConnection();
            urlConnection.setConnectTimeout(KNOWLEDGE_UPLOAD_CONNECTION_TIMEOUT);

            InputStream inputStream = urlConnection.getInputStream();
            byte[] imageData = IOUtils.toByteArray(inputStream);
            if (imageData.length == 0) {
                return "";
            }

            diagramBase64 = new String(imageData);
            String[] arr = diagramBase64.split("base64,");
            String pureBase64 = arr[1];
            byte[] pureBytes = Base64.getDecoder().decode(pureBase64);

            if (diagramBase64.contains(BASE64_DEFAULT_HEAD)) {
                String image = tmpDir.get() + "/" + UUID.randomUUID() + ".png";
                FileUtils.writeByteArrayToFile(new File(image), pureBytes);
                return image;
            }

            String svgSource = new String(pureBytes, StandardCharsets.UTF_8);
            svgSource = removeSwitchTags(svgSource);
            PNGTranscoder transcoder = new PNGTranscoder();
            TranscoderInput input = new TranscoderInput(new ByteArrayInputStream(svgSource.getBytes()));
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            TranscoderOutput output = new TranscoderOutput(byteArrayOutputStream);
            transcoder.transcode(input, output);
            pngBytes = byteArrayOutputStream.toByteArray();

            String image = tmpDir.get() + "/" + UUID.randomUUID() + ".png";
            FileUtils.writeByteArrayToFile(new File(image), pngBytes);
            return image;
        } catch (Exception e) {
            log.error("读取drawio图片出错！失败链接{} Error Message:{}", drawIoUrl, ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("读取架构图文件失败!");
        }
    }

    private String removeSwitchTags(String xmlStr) {
        Document document = Jsoup.parse(xmlStr);
        Elements switches = document.select("switch");
        for (Element switchElement : switches) {
            Element parent = switchElement.parent();
            if (!parent.tagName().equals("g")) {
                continue;
            }
            parent.append(String.valueOf(switchElement.children().last()));
            switchElement.remove();
        }

        Element svg = document.select("svg").first();
        String content = svg.attr("content");
        svg.attr("content", content.replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll("\"", "&quot;"));

        return SVG_HTML_HEAD + svg.outerHtml();
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
