package com.ytdevops.knowledgebase;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ImageInfoDto {
    private String imgPath = "";
    private Float width;
    private Float height;

    private final static Float MAX_IMAGE_WIDTH = 540F;

    public void getReasonableSize() {
        if (width < MAX_IMAGE_WIDTH) {
            return;
        }
        height = MAX_IMAGE_WIDTH / width * height;
        width = MAX_IMAGE_WIDTH;
    }
}
