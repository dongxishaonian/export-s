package com.ytdevops.knowledgebase;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class KnowledgeDocDto {
    private String title;
    private String contentHtml;
}
