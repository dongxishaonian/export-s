package com.ytdevops.file;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
@Accessors(chain = true)
@Slf4j
public class FileDetailDto {
    private Meta meta;

    private String format;

    private List<Data> data;

    @lombok.Data
    @Accessors(chain = true)
    @Slf4j
    public static class Data {
        private String nodeUuid;

        private String description;

        private String id;

        private String key;

        private String topic;

        private Integer index;

        private boolean expanded;

        @JsonProperty("parentid")
        private String parentId;

        private boolean isroot;

        @JsonProperty("background-color")
        private String backgroundColor;

        private List<Content> content = new ArrayList<>();

        private List<Attachment> attachments = new ArrayList<>();

        @JsonProperty("foreground-color")
        private String foregroundColor;

        private String status;

        private Integer layer;

        private String fileTypeId;

        private String tenantId;

        private String projectId;

        private String fileId;

        private List<TestDetail> testDetails;

        private String testPlanId;

        private String testCase;

        private Integer storyPoint;

        private List<ArchitectureDiagram> architectureDiagrams;

        private Long createdBy;

        private String updatedAt;

        private Long assignee;

        private String createdAt;

    }

    @lombok.Data
    @Accessors(chain = true)
    public static class TestDetail {
        private String preConditions;

        private String testingProcedure;

        private String expectedResult;

        private String actualResult;

        private String testResult;
    }

    @lombok.Data
    @Accessors(chain = true)
    public static class ArchitectureDiagram {
        private String id;
        private String name;
        private String data;
    }

    @lombok.Data
    @Accessors(chain = true)
    @Slf4j
    public static class Content implements Serializable {
        private String fieldReferenceId;
        private String fieldId;
        private Integer orderNumber;
        private String name;
        private FieldType fieldType;
        private Boolean required;
        private Boolean show;
        private List<ExtraSelectData> extraData;
        private Object value;

        @JsonIgnore
        public String getStringValue() {
            if (fieldType.getIsList()) {
                throw new RuntimeException("数据转换异常！");
            }
            return String.valueOf(value);
        }

        public Boolean isEmptyContent() {
            if (Objects.isNull(value)) {
                return true;
            }

            if (fieldType.getIsList()) {
                try {
                    String writeValueAsString = new ObjectMapper().writeValueAsString(value);
                    TypeReference<List<String>> listTypeReference = new TypeReference<List<String>>() {
                    };
                    List<String> list = new ObjectMapper().readValue(writeValueAsString, listTypeReference);
                    return CollectionUtils.isEmpty(list);
                } catch (JsonProcessingException e) {
                    log.error("isEmptyContent error:{}", ExceptionUtils.getStackTrace(e));
                    return true;
                }
            }

            return StringUtils.isBlank(String.valueOf(value));
        }
    }

    @lombok.Data
    @Accessors(chain = true)
    public static class Meta {
        private String name;

        private String author = "AutoMind";

        private String version = "0";

        private String key;
    }

    @lombok.Data
    @Accessors(chain = true)
    public static class Attachment {
        private String name;
        private String url;
    }
}
