package com.ytdevops.file;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FieldType {
    TEXT(false),
    TEXTAREA(false),
    NUMBER(false),
    SELECT(false),
    SELECT_MULTI(true),
    SINGLE_USER_SELECT(false),
    MULTI_USER_SELECT(true),
    DATE(false),
    LABEL(true),
    VERSION(false),
    LINK(false),
    DRAWIO(false),
    STATUS(false),
    ARRAY(true),
    RICH_TEXT(false),
    ATTACHMENT(false),
    POSITIVE_INTEGER(false),
    MULTI_USER_GROUP_SELECT(true);
    private final Boolean isList;

    public static Boolean isUserType(FieldType fieldType) {
        return fieldType.equals(SINGLE_USER_SELECT) || fieldType.equals(MULTI_USER_SELECT);
    }
}
