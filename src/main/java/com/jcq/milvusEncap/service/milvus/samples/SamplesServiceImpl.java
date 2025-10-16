package com.jcq.milvusEncap.service.milvus.samples;

import com.jcq.milvusEncap.controller.samples.vo.SamplesPageReqVO;
import com.jcq.milvusEncap.controller.samples.vo.SamplesSaveReqVO;
import com.jcq.milvusEncap.dal.dataobject.agent.SamplesDO;
import com.jcq.milvusEncap.dal.pojo.PageResult;
import com.jcq.milvusEncap.service.milvus.MilvusBaseService;
import com.jcq.milvusEncap.service.milvus.MilvusLambdaQueryWrapper;
import com.jcq.milvusEncap.util.BeanUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 向量数据库 样例库 Service 实现类
 *
 * @author : jucunqi
 * @since : 2025/10/14
 */
@Slf4j
@Service
public class SamplesServiceImpl extends MilvusBaseService<SamplesDO> implements SamplesService {

    @Override
    public Long createSamples(SamplesSaveReqVO createReqVO) {

        SamplesDO samplesDO = getSamplesDO(createReqVO);
        return insert(samplesDO);
    }

    @Override
    public boolean deleteSamples(Long sampleId) {

         return deleteById(sampleId);
    }

    @Override
    public void updateSamples(SamplesSaveReqVO updateReqVO) {

        SamplesDO samplesDO = getSamplesDO(updateReqVO);
        updateById(samplesDO);
    }

    @Override
    public PageResult<SamplesDO> getSamplesPage(SamplesPageReqVO pageReqVO) {

        // 构建请求条件
        MilvusLambdaQueryWrapper<SamplesDO> wrapper = new MilvusLambdaQueryWrapper<SamplesDO>()
                .likeIfPresent(SamplesDO::getAgentName, pageReqVO.getAgentName())
                .likeIfPresent(SamplesDO::getSampleQuestion, pageReqVO.getSampleQuestion())
                .likeIfPresent(SamplesDO::getSampleAnswer, pageReqVO.getSampleAnswer());

        // 分页查询
        return selectPage(pageReqVO, wrapper);
    }

    @Override
    public SamplesDO getSampleInfo(Long sampleId) {

        return getById(sampleId);
    }

    private SamplesDO getSamplesDO(SamplesSaveReqVO createReqVO) {

        SamplesDO samplesDO = BeanUtils.toBean(createReqVO, SamplesDO.class);

        // todo：生成问题字段的向量 ，就不给出示例代码了
        String questionText = samplesDO.getSampleQuestion();
        float[] embeddingResult = null;
        samplesDO.setSampleVector(embeddingResult);

        // 默认时间
        long currentTime = System.currentTimeMillis() / 1000;
        samplesDO.setCreatedTime(currentTime);
        samplesDO.setUpdatedTime(currentTime);
        return samplesDO;
    }
}
