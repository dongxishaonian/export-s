package com.ytdevops.file;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ExtraSelectData {
    private String label;
    private String value;
}
