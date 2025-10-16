package com.jcq.milvusEncap.controller.samples.vo;

import lombok.Data;

@Data
public class SamplesSaveReqVO {

    private Long sampleId;
    private Long agentId;
    private String agentName;
    private String sampleQuestion;
    private String sampleAnswer;
    private float[] sampleVector;
    private String sampleStatus;
}
