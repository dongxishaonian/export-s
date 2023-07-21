package com.ytdevops.knowledgebase;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class HtmlToPdfDto {
    private String contentHtml;
    private String title;
}
