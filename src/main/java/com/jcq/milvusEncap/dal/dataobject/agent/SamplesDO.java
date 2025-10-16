package com.jcq.milvusEncap.dal.dataobject.agent;

import com.jcq.milvusEncap.annotation.CollectionName;
import com.jcq.milvusEncap.annotation.PrimaryKey;
import lombok.*;

@CollectionName("biz_samples")
@Data
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SamplesDO {

    /**
     * 唯一id
     */
    @PrimaryKey
    private Long sampleId;
    /**
     * 智能体id
     */
    private Long agentId;
    /**
     * 智能体名称
     */
    private String agentName;
    /**
     * 问题
     */
    private String sampleQuestion;
    /**
     * 答案
     */
    private String sampleAnswer;
    /**
     * 向量
     */
    private float[] sampleVector;
    /**
     * 状态
     */
    private String sampleStatus;
    /**
     * 创建时间
     */
    private Long createdTime;
    /**
     * 更新时间
     */
    private Long updatedTime;
}
