package com.couponapi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderRequestDto {
    @NotBlank(message = "No customerKey")
    @Size(max = 20, message = "Too long customerKey")
    private String customerKey;

    @NotBlank(message = "No customerTrxId")
    @Size(max = 30, message = "Too long customerTrxId")
    private String customerTrxId;

    @NotBlank(message = "No customerGoodsCode")
    @Size(max = 30, message = "Too long customerGoodsCode")
    private String customerGoodsCode;

    @Min(value = 1, message = "No quantity")
    @Max(value = 1000, message = "quantity maximum 1000")
    private int quantity;

    @Size(max = 100, message = "Too long msgSubject")
    private String msgSubject;

    @Size(max = 100, message = "Too long msgAddContent")
    private String msgAddContent;
}