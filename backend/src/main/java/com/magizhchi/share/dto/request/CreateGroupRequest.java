package com.magizhchi.share.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class CreateGroupRequest {
    @NotBlank @Size(min = 1, max = 80)
    private String name;
    private List<Long> memberIds;
}
