package com.jcq.milvusEncap.controller.samples.vo;

import lombok.Data;

@Data
public class SamplesRespVO {

    private Long sampleId;
    private Long agentId;
    private String agentName;
    private String sampleQuestion;
    private String sampleAnswer;
    private float[] sampleVector;
    private String sampleStatus;
    private Long createdTime;
    private Long updatedTime;
}
