package com.jcq.milvusEncap.controller.samples;

import com.jcq.milvusEncap.controller.samples.vo.SamplesPageReqVO;
import com.jcq.milvusEncap.controller.samples.vo.SamplesRespVO;
import com.jcq.milvusEncap.controller.samples.vo.SamplesSaveReqVO;
import com.jcq.milvusEncap.dal.dataobject.agent.SamplesDO;
import com.jcq.milvusEncap.dal.pojo.CommonResult;
import com.jcq.milvusEncap.dal.pojo.PageResult;
import com.jcq.milvusEncap.service.milvus.samples.SamplesService;
import com.jcq.milvusEncap.util.BeanUtils;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.jcq.milvusEncap.dal.pojo.CommonResult.success;

@RestController
@RequestMapping("/helper/samples")
@Validated
public class SamplesController {

    @Resource
    private SamplesService samplesService;

    @GetMapping("/get")
    public CommonResult<SamplesRespVO> getInfo(@RequestParam("sampleId") Long sampleId) {
        SamplesDO info = samplesService.getSampleInfo(sampleId);
        return success(BeanUtils.toBean(info, SamplesRespVO.class));
    }

    @PostMapping("/create")
    public CommonResult<Long> createSamples(@RequestBody SamplesSaveReqVO createReqVO) {
        return success(samplesService.createSamples(createReqVO));
    }

    @DeleteMapping("/delete")
    public CommonResult<Boolean> deleteSamples(@RequestParam("sampleId") Long sampleId) {
        samplesService.deleteSamples(sampleId);
        return success(true);
    }

    @PutMapping("/update")
    public CommonResult<Boolean> updateSamples(@RequestBody SamplesSaveReqVO updateReqVO) {
        samplesService.updateSamples(updateReqVO);
        return success(true);
    }

    @GetMapping("/page")
    public CommonResult<PageResult<SamplesRespVO>> getSamplesPage(SamplesPageReqVO pageReqVO) {
        PageResult<SamplesDO> pageResult = samplesService.getSamplesPage(pageReqVO);
        PageResult<SamplesRespVO> result = BeanUtils.toBean(pageResult, SamplesRespVO.class);
        return success(result);
    }
}
