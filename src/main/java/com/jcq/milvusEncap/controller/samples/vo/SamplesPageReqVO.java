package com.jcq.milvusEncap.controller.samples.vo;

import com.jcq.milvusEncap.dal.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class SamplesPageReqVO extends PageParam {

    private String agentName;
    private String sampleQuestion;
    private String sampleAnswer;
}
