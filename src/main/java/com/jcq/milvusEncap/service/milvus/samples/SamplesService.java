package com.jcq.milvusEncap.service.milvus.samples;


import com.jcq.milvusEncap.controller.samples.vo.SamplesPageReqVO;
import com.jcq.milvusEncap.controller.samples.vo.SamplesSaveReqVO;
import com.jcq.milvusEncap.dal.dataobject.agent.SamplesDO;
import com.jcq.milvusEncap.dal.pojo.PageResult;

/**
 * 向量数据库样例库 Service 接口
 *
 * @author : jucunqi
 * @since : 2025/10/16
 */
public interface SamplesService {

    /**
     * 创建向量数据库样例库
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createSamples(SamplesSaveReqVO createReqVO);

    /**
     * 删除向量数据库样例库
     *
     * @param sampleId 编号
     * @return 是否删除成功
     */
    boolean deleteSamples(Long sampleId);

    /**
     * 更新向量数据库样例库
     *
     * @param updateReqVO 更新信息
     */
    void updateSamples(SamplesSaveReqVO updateReqVO);

    /**
     * 获取向量数据库样例库分页
     *
     * @param pageReqVO 分页查询
     * @return 向量数据库样例库分页
     */
    PageResult<SamplesDO> getSamplesPage(SamplesPageReqVO pageReqVO);

    /**
     * 获取样例库信息
     * @param sampleId 主键id
     * @return 数据
     */
    SamplesDO getSampleInfo(Long sampleId);
}
