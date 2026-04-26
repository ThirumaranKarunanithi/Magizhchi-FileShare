package com.magizhchi.share.websocket;

import com.magizhchi.share.dto.response.FileMessageResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileMessageEvent {
    /** Event type: NEW_FILE | FILE_DELETED | MEMBER_ADDED | MEMBER_REMOVED */
    private String              type;
    private FileMessageResponse payload;
}
